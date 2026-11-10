package ir.hw2.rank;

import ir.hw2.index.IndexReader;
import ir.hw2.index.PostingList;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;

/**
 * 向量空間模型排序：term-at-a-time 累加內積，再以 heap 取前 k 名。（工作三）
 */
public final class Ranker {

    /** 一筆搜尋結果。 */
    public record Hit(int docId, double score) {
    }

    /**
     * @param hits    前 k 名
     * @param matched 參與排序的文件總數
     */
    public record Ranking(List<Hit> hits, int matched) {
    }

    private static final double K1 = 1.2;
    private static final double B = 0.75;

    /** 分數高者在前；同分時 docId 小者在前。 */
    private static final Comparator<Hit> BEST_FIRST =
            Comparator.comparingDouble(Hit::score).reversed().thenComparingInt(Hit::docId);

    private final IndexReader index;

    public Ranker(IndexReader index) {
        this.index = index;
    }

    /**
     * @param queryTerms 查詢詞 → 在查詢中出現的次數
     * @param candidates 只在這些文件中排序（遞增 docId）；null 表示「含任一查詢詞的文件」
     * @param topK       回傳前幾名；小於等於 0 表示全部
     * @param champion   true 時只讀 champion list（非精確 top-k）
     */
    public Ranking rank(Map<String, Integer> queryTerms, int[] candidates, Weighting weighting,
                          int topK, boolean champion) {
        int n = index.documentCount();
        double[] accumulator = new double[n + 1];
        boolean[] touched = new boolean[n + 1];
        boolean[] allowed = null;
        if (candidates != null) {
            allowed = new boolean[n + 1];
            for (int docId : candidates) {
                allowed[docId] = true;
            }
        }

        double querySquares = 0;
        for (Map.Entry<String, Integer> entry : queryTerms.entrySet()) {
            int df = index.df(entry.getKey());
            if (df == 0) {
                continue;
            }
            double idf = Math.log10((double) n / df);
            double queryWeight = (1 + Math.log10(entry.getValue())) * idf;
            querySquares += queryWeight * queryWeight;

            PostingList postings = champion ? index.champions(entry.getKey()) : index.postings(entry.getKey());
            int[] docIds = postings.docIds();
            int[] tfs = postings.tfs();
            double bm25Idf = Math.log(1 + (n - df + 0.5) / (df + 0.5));
            for (int i = 0; i < docIds.length; i++) {
                int docId = docIds[i];
                if (allowed != null && !allowed[docId]) {
                    continue;
                }
                touched[docId] = true;
                accumulator[docId] += switch (weighting) {
                    case TFIDF -> queryWeight * (1 + Math.log10(tfs[i])) * idf;
                    case LNC_LTC -> queryWeight * (1 + Math.log10(tfs[i]));
                    case BM25 -> entry.getValue() * bm25Idf * tfs[i] * (K1 + 1)
                            / (tfs[i] + K1 * (1 - B + B * index.length(docId) / index.averageLength()));
                };
            }
        }
        double queryNorm = Math.sqrt(querySquares);

        // 最小堆：堆頂是目前前 k 名中最差的一筆
        PriorityQueue<Hit> heap = new PriorityQueue<>(BEST_FIRST.reversed());
        int matched = 0;
        if (candidates != null) {
            matched = candidates.length;
            for (int docId : candidates) {
                offer(heap, new Hit(docId, finalScore(accumulator[docId], docId, queryNorm, weighting)), topK);
            }
        } else {
            for (int docId = 1; docId <= n; docId++) {
                if (touched[docId]) {
                    matched++;
                    offer(heap, new Hit(docId, finalScore(accumulator[docId], docId, queryNorm, weighting)), topK);
                }
            }
        }
        List<Hit> hits = new ArrayList<>(heap);
        hits.sort(BEST_FIRST);
        return new Ranking(hits, matched);
    }

    private double finalScore(double dot, int docId, double queryNorm, Weighting weighting) {
        if (weighting == Weighting.BM25) {
            return dot;
        }
        double docNorm = weighting == Weighting.TFIDF ? index.tfidfNorm(docId) : index.lncNorm(docId);
        double denominator = docNorm * queryNorm;
        return denominator == 0 ? 0 : dot / denominator;
    }

    private static void offer(PriorityQueue<Hit> heap, Hit hit, int topK) {
        if (topK <= 0 || heap.size() < topK) {
            heap.add(hit);
        } else if (BEST_FIRST.compare(hit, heap.peek()) < 0) {
            heap.poll();
            heap.add(hit);
        }
    }
}
