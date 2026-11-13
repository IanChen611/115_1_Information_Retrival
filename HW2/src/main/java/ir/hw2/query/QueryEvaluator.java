package ir.hw2.query;

import ir.hw1.ext.SkipList;
import ir.hw2.index.IndexReader;
import ir.hw2.index.PostingList;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * 依語法樹求出符合查詢的文件集合（遞增的 docId）。（工作二）
 */
public final class QueryEvaluator {

    /** 一個前綴最多展開成幾個詞（取 df 最高者）。 */
    public static final int MAX_PREFIX_EXPANSION = 500;

    private final IndexReader index;

    public QueryEvaluator(IndexReader index) {
        this.index = index;
    }

    /** 符合 node 的所有 docId（遞增）。 */
    public int[] evaluate(QueryNode node) {
        if (node instanceof QueryNode.Term term) {
            return index.postings(term.term()).docIds();
        } else if (node instanceof QueryNode.Prefix prefix) {
            int[] result = new int[0];
            for (String term : expand(prefix.prefix())) {
                result = union(result, index.postings(term).docIds());
            }
            return result;
        } else if (node instanceof QueryNode.Phrase phrase) {
            return phrase(phrase);
        } else if (node instanceof QueryNode.Proximity proximity) {
            return proximity(proximity);
        } else if (node instanceof QueryNode.Or or) {
            int[] result = new int[0];
            for (QueryNode child : or.children()) {
                result = union(result, evaluate(child));
            }
            return result;
        } else if (node instanceof QueryNode.And and) {
            return and(and);
        } else if (node instanceof QueryNode.Not not) {
            return difference(allDocuments(), evaluate(not.child()));
        }
        throw new IllegalArgumentException("unknown node: " + node);
    }

    /**
     * 收集參與計分的詞與其在查詢中的次數（NOT 底下的詞不計分）。
     */
    public void collectTerms(QueryNode node, Map<String, Integer> out) {
        if (node instanceof QueryNode.Term term) {
            out.merge(term.term(), 1, Integer::sum);
        } else if (node instanceof QueryNode.Prefix prefix) {
            for (String term : expand(prefix.prefix())) {
                out.merge(term, 1, Integer::sum);
            }
        } else if (node instanceof QueryNode.Phrase phrase) {
            for (String term : phrase.terms()) {
                out.merge(term, 1, Integer::sum);
            }
        } else if (node instanceof QueryNode.Proximity proximity) {
            out.merge(proximity.left(), 1, Integer::sum);
            out.merge(proximity.right(), 1, Integer::sum);
        } else if (node instanceof QueryNode.Or or) {
            or.children().forEach(child -> collectTerms(child, out));
        } else if (node instanceof QueryNode.And and) {
            and.children().forEach(child -> collectTerms(child, out));
        }
    }

    /** 前綴展開：在排序詞典上取前綴區間；過多時只留 df 最高的 {@link #MAX_PREFIX_EXPANSION} 個。 */
    public List<String> expand(String prefix) {
        List<String> terms = index.termsWithPrefix(prefix);
        if (terms.size() <= MAX_PREFIX_EXPANSION) {
            return terms;
        }
        List<String> sorted = new ArrayList<>(terms);
        sorted.sort(Comparator.comparingInt((String t) -> -index.df(t)).thenComparing(Comparator.naturalOrder()));
        return sorted.subList(0, MAX_PREFIX_EXPANSION);
    }

    private int[] and(QueryNode.And and) {
        // 先交集正向運算元（由短到長），再扣掉 NOT 運算元
        List<int[]> positives = new ArrayList<>();
        List<int[]> negatives = new ArrayList<>();
        for (QueryNode child : and.children()) {
            if (child instanceof QueryNode.Not not) {
                negatives.add(evaluate(not.child()));
            } else {
                positives.add(evaluate(child));
            }
        }
        int[] result;
        if (positives.isEmpty()) {
            result = allDocuments();
        } else {
            positives.sort(Comparator.comparingInt(a -> a.length));
            result = positives.get(0);
            for (int i = 1; i < positives.size() && result.length > 0; i++) {
                result = intersect(result, positives.get(i));
            }
        }
        for (int[] negative : negatives) {
            result = difference(result, negative);
        }
        return result;
    }

    private int[] phrase(QueryNode.Phrase phrase) {
        int n = phrase.terms().size();
        PostingList[] lists = new PostingList[n];
        int[] common = null;
        for (int i = 0; i < n; i++) {
            lists[i] = index.postings(phrase.terms().get(i));
            common = common == null ? lists[i].docIds() : intersect(common, lists[i].docIds());
        }
        int[] result = new int[common.length];
        int count = 0;
        for (int docId : common) {
            int[][] positions = new int[n][];
            for (int i = 0; i < n; i++) {
                positions[i] = lists[i].positions()[Arrays.binarySearch(lists[i].docIds(), docId)];
            }
            for (int start : positions[0]) {
                boolean match = true;
                for (int i = 1; i < n && match; i++) {
                    match = Arrays.binarySearch(positions[i], start + phrase.offsets().get(i)) >= 0;
                }
                if (match) {
                    result[count++] = docId;
                    break;
                }
            }
        }
        return Arrays.copyOf(result, count);
    }

    private int[] proximity(QueryNode.Proximity proximity) {
        PostingList left = index.postings(proximity.left());
        PostingList right = index.postings(proximity.right());
        int[] common = intersect(left.docIds(), right.docIds());
        int[] result = new int[common.length];
        int count = 0;
        for (int docId : common) {
            int[] a = left.positions()[Arrays.binarySearch(left.docIds(), docId)];
            int[] b = right.positions()[Arrays.binarySearch(right.docIds(), docId)];
            int i = 0;
            int j = 0;
            while (i < a.length && j < b.length) {
                int gap = a[i] - b[j];
                if (gap != 0 && Math.abs(gap) <= proximity.distance()) {
                    result[count++] = docId;
                    break;
                }
                if (a[i] < b[j]) {
                    i++;
                } else {
                    j++;
                }
            }
        }
        return Arrays.copyOf(result, count);
    }

    /** AND：使用 HW1 的 skip pointer 交集。 */
    private static int[] intersect(int[] a, int[] b) {
        if (a.length == 0 || b.length == 0) {
            return new int[0];
        }
        return SkipList.intersect(new SkipList(a), new SkipList(b));
    }

    private static int[] union(int[] a, int[] b) {
        if (a.length == 0) {
            return b;
        }
        if (b.length == 0) {
            return a;
        }
        int[] out = new int[a.length + b.length];
        int i = 0;
        int j = 0;
        int n = 0;
        while (i < a.length && j < b.length) {
            if (a[i] == b[j]) {
                out[n++] = a[i++];
                j++;
            } else if (a[i] < b[j]) {
                out[n++] = a[i++];
            } else {
                out[n++] = b[j++];
            }
        }
        while (i < a.length) {
            out[n++] = a[i++];
        }
        while (j < b.length) {
            out[n++] = b[j++];
        }
        return Arrays.copyOf(out, n);
    }

    private static int[] difference(int[] a, int[] b) {
        int[] out = new int[a.length];
        int j = 0;
        int n = 0;
        for (int value : a) {
            while (j < b.length && b[j] < value) {
                j++;
            }
            if (j >= b.length || b[j] != value) {
                out[n++] = value;
            }
        }
        return Arrays.copyOf(out, n);
    }

    private int[] allDocuments() {
        int[] all = new int[index.documentCount()];
        for (int i = 0; i < all.length; i++) {
            all[i] = i + 1;
        }
        return all;
    }
}
