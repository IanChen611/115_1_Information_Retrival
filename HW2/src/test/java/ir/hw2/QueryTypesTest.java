package ir.hw2;

import ir.hw2.index.IndexReader;
import ir.hw2.index.PostingList;
import ir.hw2.query.QuerySyntaxException;
import ir.hw2.rank.Ranker;
import ir.hw2.rank.Weighting;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 各種查詢型態找到的文件集合。（工作四）
 *
 * <pre>
 * 1: open source software for linux        open(1) sourc(2) softwar(3) linux(5)
 * 2: the source code is open               sourc(2) code(3) open(5)
 * 3: linux kernel source                   linux(1) kernel(2) sourc(3)
 * 4: open the door                         open(1) door(3)
 * 5: software engineer and project manager
 * 6: project manager for open source       project(1) manag(2) open(4) sourc(5)
 * </pre>
 */
class QueryTypesTest {

    @TempDir
    Path dir;
    private SearchEngine engine;

    @BeforeEach
    void buildIndex() throws IOException {
        // championSize = 1：df 大於 1 的詞都只留 tf 最高的一篇
        TestIndex.build(dir, 1,
                "open source software for linux",
                "the source code is open",
                "linux kernel source",
                "open the door",
                "software engineer and project manager",
                "project manager for open source");
        engine = new SearchEngine(dir);
    }

    @AfterEach
    void close() throws IOException {
        engine.close();
    }

    private Set<Integer> docs(String query) {
        Set<Integer> found = new TreeSet<>();
        for (Ranker.Hit hit : engine.search(query, Weighting.TFIDF, 0, false).hits()) {
            found.add(hit.docId());
        }
        return found;
    }

    @Test
    void freeTextMatchesAnyTerm() {
        assertEquals(Set.of(1, 3, 4), docs("linux door"));
    }

    @Test
    void andRequiresEveryWordOfBothOperands() {
        assertEquals(Set.of(1), docs("open source AND Linux"));
    }

    @Test
    void orTakesUnionOfMultiWordOperands() {
        assertEquals(Set.of(5, 6), docs("software engineer OR project manager"));
    }

    @Test
    void notAndParenthesesCombine() {
        assertEquals(Set.of(4), docs("open AND NOT source"));
        assertEquals(Set.of(3, 5), docs("NOT open"));
        assertEquals(Set.of(1, 4), docs("(linux OR door) AND open"));
        // AND 優先於 OR
        assertEquals(Set.of(1, 3, 4), docs("linux OR door AND open"));
    }

    @Test
    void booleanResultsAreStillRankedByCosine() {
        List<Ranker.Hit> hits = engine.search("linux OR door", Weighting.TFIDF, 0, false).hits();
        assertEquals(3, hits.size());
        for (int i = 1; i < hits.size(); i++) {
            assertTrue(hits.get(i - 1).score() >= hits.get(i).score());
        }
        assertTrue(hits.get(0).score() > 0 && hits.get(0).score() <= 1.0 + 1e-12);
    }

    @Test
    void phraseNeedsAdjacentPositions() {
        assertEquals(Set.of(1, 2, 6), docs("open AND source"));
        assertEquals(Set.of(1, 6), docs("\"open source\""));
    }

    @Test
    void phraseKeepsGapLeftByStopwords() {
        // "is" 被移除但仍佔一個位置：sourc、code、open 的位置差為 0、1、3
        assertEquals(Set.of(2), docs("\"source code is open\""));
        assertEquals(Set.of(), docs("\"source code open\""));
    }

    @Test
    void proximityAllowsEitherOrderWithinDistance() {
        assertEquals(Set.of(1, 6), docs("open /1 source"));
        assertEquals(Set.of(1, 2, 6), docs("open /3 source"));
    }

    @Test
    void prefixWildcardExpandsThroughTrie() {
        assertEquals(Set.of(1, 2, 3, 6), docs("sour*"));
        assertEquals(Set.of(1, 5, 6), docs("soft* OR proj*"));
    }

    @Test
    void misspelledWordIsCorrectedAndReported() {
        SearchEngine.Result result = engine.search("linxu", Weighting.TFIDF, 0, false);
        assertEquals(Set.of(1, 3), new TreeSet<>(result.hits().stream().map(Ranker.Hit::docId).toList()));
        assertTrue(result.notes().stream().anyMatch(note -> note.contains("linux")));
    }

    @Test
    void stopwordOnlyQueryReturnsNothing() {
        SearchEngine.Result result = engine.search("the and of", Weighting.TFIDF, 0, false);
        assertTrue(result.hits().isEmpty());
        assertFalse(result.notes().isEmpty());
    }

    @Test
    void malformedQueriesAreRejected() {
        assertThrows(QuerySyntaxException.class, () -> docs("open AND"));
        assertThrows(QuerySyntaxException.class, () -> docs("(open source"));
        assertThrows(QuerySyntaxException.class, () -> docs("open source)"));
        assertThrows(QuerySyntaxException.class, () -> docs("\"open source"));
    }

    @Test
    void championModeReturnsSubsetOfExactResults() {
        Set<Integer> exact = docs("open source");
        Set<Integer> approximate = new TreeSet<>();
        for (Ranker.Hit hit : engine.search("open source", Weighting.TFIDF, 0, true).hits()) {
            approximate.add(hit.docId());
        }
        assertFalse(approximate.isEmpty());
        assertTrue(exact.containsAll(approximate));
        assertTrue(approximate.size() < exact.size());
    }

    @Test
    void lexiconOffsetsReadBackTheRightPostings() {
        IndexReader index = engine.index();
        assertEquals(6, index.documentCount());
        PostingList open = index.postings("open");
        assertArrayEquals(new int[]{1, 2, 4, 6}, open.docIds());
        assertArrayEquals(new int[]{1, 1, 1, 1}, open.tfs());
        assertArrayEquals(new int[]{5}, open.positions()[1]);
        PostingList source = index.postings("sourc");
        assertArrayEquals(new int[]{1, 2, 3, 6}, source.docIds());
        assertArrayEquals(new int[]{5}, source.positions()[3]);
        assertEquals(0, index.postings("nosuchterm").df());
        // 每個詞典項目都要能讀回自己的 postings
        for (String term : index.terms()) {
            assertEquals(term, index.postings(term).term());
            assertEquals(index.df(term), index.postings(term).df());
        }
    }
}
