package ir.hw2;

import ir.hw2.rank.Ranker;
import ir.hw2.rank.Weighting;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 以手算的小語料驗證 TF-IDF 權重與餘弦相似度。（工作四）
 *
 * <pre>
 * d1: apple banana apple          → appl ×2, banana ×1
 * d2: banana cherry               → banana ×1, cherri ×1
 * d3: apple cherry cherry cherry date → appl ×1, cherri ×3, date ×1
 * N = 3；df(appl) = df(banana) = df(cherri) = 2，df(date) = 1
 * idf2 = log10(3/2) = 0.176091，idf1 = log10(3) = 0.477121
 * </pre>
 */
class RankingTest {

    private static final double IDF2 = Math.log10(3.0 / 2.0);
    private static final double IDF1 = Math.log10(3.0);

    @TempDir
    Path dir;
    private SearchEngine engine;

    @BeforeEach
    void buildIndex() throws IOException {
        TestIndex.build(dir, 50,
                "apple banana apple",
                "banana cherry",
                "apple cherry cherry cherry date");
        engine = new SearchEngine(dir);
    }

    @AfterEach
    void close() throws IOException {
        engine.close();
    }

    private static double logTf(int tf) {
        return 1 + Math.log10(tf);
    }

    @Test
    void singleKeywordScoreMatchesHandComputedCosine() {
        // d3 的向量：appl 0.176091、cherri 1.477121×0.176091 = 0.260108、date 0.477121
        // |d3| = sqrt(0.031008 + 0.067656 + 0.227644) = 0.571234；查詢只有 date，cos = 0.477121 / 0.571234
        List<Ranker.Hit> hits = engine.search("date", Weighting.TFIDF, 10, false).hits();
        assertEquals(1, hits.size());
        assertEquals(3, hits.get(0).docId());
        assertEquals(0.835246, hits.get(0).score(), 1e-4);
    }

    @Test
    void freeTextRanksByCosineOfTfIdfVectors() {
        double norm1 = Math.sqrt(Math.pow(logTf(2) * IDF2, 2) + Math.pow(IDF2, 2));
        double norm2 = Math.sqrt(2 * IDF2 * IDF2);
        double norm3 = Math.sqrt(IDF2 * IDF2 + Math.pow(logTf(3) * IDF2, 2) + IDF1 * IDF1);
        double queryNorm = Math.sqrt(2 * IDF2 * IDF2);
        double score1 = IDF2 * logTf(2) * IDF2 / (norm1 * queryNorm);
        double score2 = IDF2 * IDF2 / (norm2 * queryNorm);
        double score3 = (IDF2 * IDF2 + IDF2 * logTf(3) * IDF2) / (norm3 * queryNorm);

        List<Ranker.Hit> hits = engine.search("apple cherry", Weighting.TFIDF, 10, false).hits();
        assertEquals(3, hits.size());
        // 手算：d1 = 0.5606、d2 = 0.5000、d3 = 0.5407
        assertEquals(List.of(1, 3, 2), hits.stream().map(Ranker.Hit::docId).toList());
        assertEquals(score1, hits.get(0).score(), 1e-9);
        assertEquals(score3, hits.get(1).score(), 1e-9);
        assertEquals(score2, hits.get(2).score(), 1e-9);
        assertEquals(0.5, score2, 1e-9);
    }

    @Test
    void queryNormalizationMatchesIndexing() {
        // 大小寫、複數與停用詞都要和建索引時一致
        List<Ranker.Hit> plain = engine.search("date", Weighting.TFIDF, 10, false).hits();
        List<Ranker.Hit> noisy = engine.search("The DATES", Weighting.TFIDF, 10, false).hits();
        assertEquals(plain, noisy);
    }

    @Test
    void documentMatchingWholeVectorScoresOne() throws IOException {
        Path other = dir.resolve("unit");
        java.nio.file.Files.createDirectories(other);
        TestIndex.build(other, 50, "alpha beta", "gamma delta");
        try (SearchEngine unit = new SearchEngine(other)) {
            List<Ranker.Hit> hits = unit.search("alpha beta", Weighting.TFIDF, 10, false).hits();
            assertEquals(1, hits.size());
            assertEquals(1.0, hits.get(0).score(), 1e-12);
        }
    }

    @Test
    void topKKeepsOnlyTheBestHitsButReportsAllMatches() {
        SearchEngine.Result result = engine.search("apple cherry", Weighting.TFIDF, 2, false);
        assertEquals(List.of(1, 3), result.hits().stream().map(Ranker.Hit::docId).toList());
        assertEquals(3, result.matched());
    }

    @Test
    void lncLtcIgnoresIdfOnTheDocumentSide() {
        // 查詢 date：文件端權重是 1 + log10 tf = 1，|d3| = sqrt(1 + 1.477121² + 1)
        double expected = 1 / Math.sqrt(1 + Math.pow(logTf(3), 2) + 1);
        List<Ranker.Hit> hits = engine.search("date", Weighting.LNC_LTC, 10, false).hits();
        assertEquals(expected, hits.get(0).score(), 1e-9);
    }

    @Test
    void bm25PrefersHigherTermFrequency() {
        List<Ranker.Hit> hits = engine.search("cherry", Weighting.BM25, 10, false).hits();
        assertEquals(List.of(3, 2), hits.stream().map(Ranker.Hit::docId).toList());
        assertTrue(hits.get(0).score() > hits.get(1).score());
    }
}
