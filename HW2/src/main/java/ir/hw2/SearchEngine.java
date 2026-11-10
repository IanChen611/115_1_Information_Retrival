package ir.hw2;

import ir.hw1.text.DefaultTokenizer;
import ir.hw1.text.StopwordList;
import ir.hw1.text.Tokenizer;
import ir.hw2.index.IndexReader;
import ir.hw2.query.QueryEvaluator;
import ir.hw2.query.QueryNode;
import ir.hw2.query.QueryParser;
import ir.hw2.rank.Ranker;
import ir.hw2.rank.Weighting;
import ir.hw2.spell.SpellCorrector;

import java.io.Closeable;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 串接「查詢解析 → 布林／位置篩選 → 向量空間排序」。（工作一）
 */
public final class SearchEngine implements Closeable {

    /**
     * @param hits     排序後的結果
     * @param matched  符合查詢的文件總數（champion 模式下是實際計分的文件數）
     * @param notes    給使用者的提示（拼字校正、查無此詞…）
     */
    public record Result(List<Ranker.Hit> hits, int matched, List<String> notes) {
    }

    private final IndexReader index;
    private final Tokenizer tokenizer;
    private final QueryEvaluator evaluator;
    private final Ranker ranker;
    private final SpellCorrector corrector;

    public SearchEngine(Path indexDirectory) throws IOException {
        index = new IndexReader(indexDirectory);
        // 查詢必須與建索引時用同一組 tokenizer 選項
        tokenizer = new DefaultTokenizer(index.options(),
                index.options().removeStopwords() ? StopwordList.loadDefault() : null);
        evaluator = new QueryEvaluator(index);
        ranker = new Ranker(index);
        corrector = new SpellCorrector(index);
    }

    public IndexReader index() {
        return index;
    }

    /**
     * @throws ir.hw2.query.QuerySyntaxException 查詢不合語法
     */
    public Result search(String query, Weighting weighting, int topK, boolean champion) {
        List<String> notes = new ArrayList<>();
        QueryParser parser = new QueryParser(tokenizer, term -> {
            if (index.contains(term)) {
                return term;
            }
            String suggestion = corrector.suggest(term);
            if (suggestion == null) {
                notes.add("索引中沒有 \"" + term + "\"");
                return term;
            }
            notes.add("索引中沒有 \"" + term + "\"，改用 \"" + suggestion + "\"");
            return suggestion;
        });
        QueryNode root = parser.parse(query);
        if (root == null) {
            notes.add("查詢經過正規化後沒有任何詞（可能全是停用詞）");
            return new Result(List.of(), 0, notes);
        }

        Map<String, Integer> terms = new LinkedHashMap<>();
        evaluator.collectTerms(root, terms);

        // 純自由文字：候選就是「含任一查詢詞的文件」，由 Ranker 邊累加邊決定，不必另外求聯集
        int[] candidates = isFreeText(root) ? null : evaluator.evaluate(root);
        Ranker.Ranking ranking = ranker.rank(terms, candidates, weighting, topK, champion);
        return new Result(ranking.hits(), ranking.matched(), notes);
    }

    private static boolean isFreeText(QueryNode root) {
        if (root instanceof QueryNode.Term) {
            return true;
        }
        return root instanceof QueryNode.Or or
                && or.children().stream().allMatch(child -> child instanceof QueryNode.Term);
    }

    @Override
    public void close() throws IOException {
        index.close();
    }
}
