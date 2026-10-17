package ir.hw1.index;

import ir.hw1.parser.Document;
import ir.hw1.parser.WarcReader;
import ir.hw1.text.DefaultTokenizer;
import ir.hw1.text.Token;
import ir.hw1.text.TokenizerOptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * C 模組的輸出、分批合併與介面契約測試。
 * 測試產物保留在 target/index-tests，方便檢查；不自動清除任何檔案。
 */
class PositionalIndexerTest {

    private Path testRoot;

    @BeforeEach
    void createRetainedTestDirectory() throws IOException {
        Path parent = Path.of("target", "index-tests");
        Files.createDirectories(parent);
        testRoot = Files.createTempDirectory(parent, "run-");
    }

    @Test
    void writesHandComputedIndexWithUnicodeCaseGapsAndRepeatedTerms() throws Exception {
        PositionalIndexer indexer = new PositionalIndexer(testRoot.resolve("work"), 1_000_000);
        indexer.addDocument(1, List.of(
                new Token("cat", 1), new Token("Sat", 2), new Token("cat", 4),
                new Token("éclair", 7), new Token("中", 8)));
        indexer.addDocument(4, List.of());
        indexer.addDocument(7, List.of(
                new Token("cat", 2), new Token("sat", 3), new Token("éclair", 5),
                new Token("Apple", 8), new Token("apple", 9)));

        Path output = testRoot.resolve("output");
        indexer.finish(output);

        assertOutput(output,
                List.of("Apple\t1", "Sat\t1", "apple\t1", "cat\t2", "sat\t1",
                        "éclair\t2", "中\t1"),
                List.of(
                        "Apple, 1: <7, 1: <8>>",
                        "Sat, 1: <1, 1: <2>>",
                        "apple, 1: <7, 1: <9>>",
                        "cat, 2: <1, 2: <1, 4>; 7, 1: <2>>",
                        "sat, 1: <7, 1: <3>>",
                        "éclair, 2: <1, 1: <7>; 7, 1: <5>>",
                        "中, 1: <1, 1: <8>>"));
    }

    @Test
    void forcedBlocksProduceExactlyTheSameBytesAsOneLargeBlock() throws Exception {
        PositionalIndexer large = new PositionalIndexer(testRoot.resolve("large-work"), 1_000_000);
        PositionalIndexer small = new PositionalIndexer(testRoot.resolve("small-work"), 1, 2);
        addSevenDocuments(large);
        addSevenDocuments(small);
        Path largeOutput = testRoot.resolve("large-output");
        Path smallOutput = testRoot.resolve("small-output");

        large.finish(largeOutput);
        small.finish(smallOutput);

        for (String filename : List.of("dictionary.txt", "postings.txt")) {
            assertArrayEquals(Files.readAllBytes(largeOutput.resolve(filename)),
                    Files.readAllBytes(smallOutput.resolve(filename)), filename);
        }
    }

    @Test
    void multipleMergeRoundsPreservePopularTermDfTfAndAllOriginalBlocks() throws Exception {
        Path work = testRoot.resolve("work");
        PositionalIndexer indexer = new PositionalIndexer(work, 1, 2);
        addSevenDocuments(indexer);
        Map<Path, byte[]> originalBlocks = snapshotFiles(work);
        assertTrue(originalBlocks.size() >= 7, "容量 1 應讓每篇非空文件寫成獨立區塊");

        Path output = testRoot.resolve("output");
        indexer.finish(output);

        assertOutput(output,
                List.of("common\t7", "word-a\t1", "word-b\t1", "word-c\t1",
                        "word-d\t1", "word-e\t1", "word-f\t1", "word-g\t1"),
                List.of(
                        "common, 7: <2, 2: <1, 4>; 5, 2: <1, 4>; 8, 2: <1, 4>; "
                                + "11, 2: <1, 4>; 14, 2: <1, 4>; 17, 2: <1, 4>; 20, 2: <1, 4>>",
                        "word-a, 1: <20, 1: <2>>",
                        "word-b, 1: <17, 1: <2>>",
                        "word-c, 1: <14, 1: <2>>",
                        "word-d, 1: <11, 1: <2>>",
                        "word-e, 1: <8, 1: <2>>",
                        "word-f, 1: <5, 1: <2>>",
                        "word-g, 1: <2, 1: <2>>"));
        assertFilesUnchanged(originalBlocks);
        assertTrue(snapshotFiles(work).size() > originalBlocks.size(),
                "多輪合併產生的中間檔也應保留");
    }

    @Test
    void zeroDocumentsProducesTwoEmptyFiles() throws Exception {
        PositionalIndexer indexer = new PositionalIndexer(testRoot.resolve("work"), 1);
        Path output = testRoot.resolve("nested").resolve("output");

        indexer.finish(output);

        assertOutput(output, List.of(), List.of());
    }

    @Test
    void emptyDocumentsStillConsumeTheirDocIds() throws Exception {
        PositionalIndexer indexer = new PositionalIndexer(testRoot.resolve("work"), 1);
        indexer.addDocument(3, List.of());
        assertThrows(IllegalArgumentException.class, () -> indexer.addDocument(3, List.of()));
        assertThrows(IllegalArgumentException.class, () -> indexer.addDocument(2, List.of()));
        indexer.addDocument(9, List.of());

        Path output = testRoot.resolve("output");
        indexer.finish(output);
        assertOutput(output, List.of(), List.of());
    }

    @Test
    void rejectsNonPositiveAndNonIncreasingDocIdsWithoutChangingIndex() throws Exception {
        PositionalIndexer indexer = new PositionalIndexer(testRoot.resolve("work"), 1);
        assertThrows(IllegalArgumentException.class,
                () -> indexer.addDocument(0, List.of(new Token("leak", 1))));
        assertThrows(IllegalArgumentException.class, () -> indexer.addDocument(-1, List.of()));
        indexer.addDocument(2, List.of(new Token("before", 1)));
        assertThrows(IllegalArgumentException.class,
                () -> indexer.addDocument(2, List.of(new Token("leak", 1))));
        assertThrows(IllegalArgumentException.class,
                () -> indexer.addDocument(1, List.of(new Token("leak", 1))));
        indexer.addDocument(9, List.of(new Token("after", 2)));

        Path output = testRoot.resolve("output");
        indexer.finish(output);
        assertOutput(output, List.of("after\t1", "before\t1"),
                List.of("after, 1: <9, 1: <2>>", "before, 1: <2, 1: <1>>"));
    }

    @Test
    void rejectsDuplicateOrDecreasingPositionsBeforeAddingAnyTokens() throws Exception {
        PositionalIndexer indexer = new PositionalIndexer(testRoot.resolve("work"), 1);
        indexer.addDocument(1, List.of(new Token("base", 1)));
        assertThrows(IllegalArgumentException.class, () -> indexer.addDocument(2,
                List.of(new Token("leak", 1), new Token("tail", 1))));
        indexer.addDocument(2, List.of(new Token("retry", 4)));
        assertThrows(IllegalArgumentException.class, () -> indexer.addDocument(3,
                List.of(new Token("leak", 5), new Token("tail", 4))));
        indexer.addDocument(3, List.of(new Token("final", 2)));

        Path output = testRoot.resolve("output");
        indexer.finish(output);
        assertOutput(output, List.of("base\t1", "final\t1", "retry\t1"),
                List.of("base, 1: <1, 1: <1>>", "final, 1: <3, 1: <2>>",
                        "retry, 1: <2, 1: <4>>"));
    }

    @Test
    void rejectsNullInputWithoutConsumingDocIdOrAddingPartialDocument() throws Exception {
        PositionalIndexer indexer = new PositionalIndexer(testRoot.resolve("work"), 1);
        assertThrows(NullPointerException.class, () -> indexer.addDocument(1, null));
        assertThrows(NullPointerException.class, () -> indexer.addDocument(1,
                Arrays.asList(new Token("leak", 1), null)));
        indexer.addDocument(1, List.of(new Token("valid", 3)));

        Path output = testRoot.resolve("output");
        indexer.finish(output);
        assertOutput(output, List.of("valid\t1"), List.of("valid, 1: <1, 1: <3>>"));
    }

    @Test
    void rejectsInvalidMemoryBudgetsAndMergeFanIn() {
        Path work = testRoot.resolve("work");
        assertThrows(IllegalArgumentException.class, () -> new PositionalIndexer(work, 0));
        assertThrows(IllegalArgumentException.class, () -> new PositionalIndexer(work, -1));
        assertThrows(IllegalArgumentException.class, () -> new PositionalIndexer(work, 1, 0));
        assertThrows(IllegalArgumentException.class, () -> new PositionalIndexer(work, 1, 1));
        assertThrows(IllegalArgumentException.class, () -> new PositionalIndexer(work, 1, -2));
    }

    @Test
    void successfulFinishRejectsFurtherAdditionsAndAnotherFinish() throws Exception {
        PositionalIndexer indexer = new PositionalIndexer(testRoot.resolve("work"), 1);
        indexer.addDocument(1, List.of(new Token("done", 1)));
        Path output = testRoot.resolve("output");
        indexer.finish(output);

        assertThrows(IllegalStateException.class,
                () -> indexer.addDocument(2, List.of(new Token("late", 1))));
        assertThrows(IllegalStateException.class, () -> indexer.finish(output));
        assertOutput(output, List.of("done\t1"), List.of("done, 1: <1, 1: <1>>"));
    }

    @Test
    void ioFailureMakesIndexerUnusableAndRetainsBlocks() throws Exception {
        Path work = testRoot.resolve("work");
        PositionalIndexer indexer = new PositionalIndexer(work, 1);
        indexer.addDocument(1, List.of(new Token("saved", 1)));
        Map<Path, byte[]> blocks = snapshotFiles(work);
        assertFalse(blocks.isEmpty());
        Path blocker = testRoot.resolve("ordinary-file");
        Files.writeString(blocker, "keep this file", StandardCharsets.UTF_8);

        assertThrows(IOException.class, () -> indexer.finish(blocker));

        assertThrows(IllegalStateException.class, () -> indexer.addDocument(2, List.of()));
        assertThrows(IllegalStateException.class,
                () -> indexer.finish(testRoot.resolve("retry-output")));
        assertEquals("keep this file", Files.readString(blocker, StandardCharsets.UTF_8));
        assertFilesUnchanged(blocks);
    }

    @Test
    void spillIoFailureRetainsExistingWorkRootFileAndRejectsFurtherUse() throws Exception {
        Path blockedWorkRoot = testRoot.resolve("ordinary-work-file");
        Files.writeString(blockedWorkRoot, "原有工作路徑內容必須保留", StandardCharsets.UTF_8);
        byte[] originalContents = Files.readAllBytes(blockedWorkRoot);
        PositionalIndexer indexer = new PositionalIndexer(blockedWorkRoot, 1);

        assertThrows(IOException.class,
                () -> indexer.addDocument(1, List.of(new Token("spill", 1))));

        assertArrayEquals(originalContents, Files.readAllBytes(blockedWorkRoot));
        assertThrows(IllegalStateException.class,
                () -> indexer.addDocument(2, List.of(new Token("late", 1))));
        assertThrows(IllegalStateException.class,
                () -> indexer.finish(testRoot.resolve("output")));
        assertArrayEquals(originalContents, Files.readAllBytes(blockedWorkRoot));
    }

    @Test
    void indexersSharingWorkRootKeepRunsAndResultsIsolated() throws Exception {
        Path sharedWork = testRoot.resolve("shared-work");
        PositionalIndexer first = new PositionalIndexer(sharedWork, 1);
        PositionalIndexer second = new PositionalIndexer(sharedWork, 1);
        first.addDocument(1, List.of(new Token("alpha", 1)));
        second.addDocument(1, List.of(new Token("beta", 2)));
        try (Stream<Path> children = Files.list(sharedWork)) {
            assertEquals(2, children.filter(Files::isDirectory).count(),
                    "同一 work root 中每個 indexer 都需要自己的工作目錄");
        }
        Map<Path, byte[]> originalBlocks = snapshotFiles(sharedWork);
        Path firstOutput = testRoot.resolve("first-output");
        Path secondOutput = testRoot.resolve("second-output");

        second.finish(secondOutput);
        first.finish(firstOutput);

        assertOutput(firstOutput, List.of("alpha\t1"), List.of("alpha, 1: <1, 1: <1>>"));
        assertOutput(secondOutput, List.of("beta\t1"), List.of("beta, 1: <1, 1: <2>>"));
        assertFilesUnchanged(originalBlocks);
    }

    @Test
    void acceptsLargestPositiveDocIdsAndPositionsWithoutOverflow() throws Exception {
        PositionalIndexer indexer = new PositionalIndexer(testRoot.resolve("work"), 1, 2);
        indexer.addDocument(1, List.of(
                new Token("edge", Integer.MAX_VALUE - 1), new Token("edge", Integer.MAX_VALUE)));
        indexer.addDocument(Integer.MAX_VALUE, List.of(new Token("edge", 1)));

        Path output = testRoot.resolve("output");
        indexer.finish(output);
        assertOutput(output, List.of("edge\t2"), List.of(
                "edge, 2: <1, 2: <2147483646, 2147483647>; 2147483647, 1: <1>>"));
    }

    @Test
    void mergesLongUtf8TermsAndUsesUtf16StringOrderForSupplementaryCharacters() throws Exception {
        String longTerm = "長".repeat(22_000);
        String supplementaryTerm = "\uD800\uDC00"; // U+10000，UTF-16 第一個 code unit 為 D800。
        String privateUseTerm = "\uE000";
        assertTrue(longTerm.getBytes(StandardCharsets.UTF_8).length > 65_535);
        PositionalIndexer indexer = new PositionalIndexer(testRoot.resolve("work"), 1, 2);
        indexer.addDocument(1, List.of(new Token(privateUseTerm, 1),
                new Token(longTerm, 3), new Token(supplementaryTerm, 5)));
        indexer.addDocument(3, List.of(new Token(supplementaryTerm, 2),
                new Token(longTerm, 4), new Token(privateUseTerm, 8)));
        indexer.addDocument(5, List.of(new Token(longTerm, 1), new Token(supplementaryTerm, 4)));
        indexer.addDocument(7, List.of(new Token(privateUseTerm, 3),
                new Token(longTerm, 6), new Token(supplementaryTerm, 9)));

        Path output = testRoot.resolve("output");
        indexer.finish(output);

        // String.compareTo 的 UTF-16 排序：長 < U+10000 < U+E000；UTF-8 byte 排序不同。
        assertOutput(output,
                List.of(longTerm + "\t4", supplementaryTerm + "\t4", privateUseTerm + "\t3"),
                List.of(longTerm + ", 4: <1, 1: <3>; 3, 1: <4>; 5, 1: <1>; 7, 1: <6>>",
                        supplementaryTerm + ", 4: <1, 1: <5>; 3, 1: <2>; 5, 1: <4>; 7, 1: <9>>",
                        privateUseTerm + ", 3: <1, 1: <1>; 3, 1: <8>; 7, 1: <3>>"));
    }

    @Test
    void integratesExistingWarcReaderTokenizerAndIndexer() throws Exception {
        Path sample = Path.of(getClass().getResource("/warc/sample2.warc").toURI());
        DefaultTokenizer tokenizer = new DefaultTokenizer(TokenizerOptions.none(), null);
        PositionalIndexer indexer = new PositionalIndexer(testRoot.resolve("work"), 1);
        int documentCount = 0;
        try (WarcReader reader = new WarcReader(List.of(sample))) {
            while (reader.hasNext()) {
                Document document = reader.next();
                indexer.addDocument(document.docId(), tokenizer.tokenize(document.text()));
                documentCount++;
            }
        }

        Path output = testRoot.resolve("output");
        indexer.finish(output);
        assertEquals(1, documentCount);
        assertOutput(output, List.of("Second\t1", "file\t1", "page\t1"),
                List.of("Second, 1: <1, 1: <1>>", "file, 1: <1, 1: <2>>",
                        "page, 1: <1, 1: <3>>"));
    }

    private static void addSevenDocuments(Indexer indexer) throws IOException {
        for (int i = 0; i < 7; i++) {
            String uniqueTerm = "word-" + (char) ('g' - i);
            indexer.addDocument(2 + 3 * i, List.of(new Token("common", 1),
                    new Token(uniqueTerm, 2), new Token("common", 4)));
        }
    }

    private static void assertOutput(Path output, List<String> dictionary, List<String> postings)
            throws IOException {
        assertArrayEquals(expectedBytes(dictionary), Files.readAllBytes(output.resolve("dictionary.txt")),
                "dictionary.txt 的排序、df、UTF-8 與格式");
        assertArrayEquals(expectedBytes(postings), Files.readAllBytes(output.resolve("postings.txt")),
                "postings.txt 的排序、df、tf、位置與格式");
    }

    private static byte[] expectedBytes(List<String> lines) {
        String text = lines.isEmpty() ? "" : String.join(System.lineSeparator(), lines) + System.lineSeparator();
        return text.getBytes(StandardCharsets.UTF_8);
    }

    private static Map<Path, byte[]> snapshotFiles(Path root) throws IOException {
        List<Path> paths;
        try (Stream<Path> walk = Files.walk(root)) {
            paths = walk.filter(Files::isRegularFile).toList();
        }
        Map<Path, byte[]> snapshot = new LinkedHashMap<>();
        for (Path path : paths) {
            snapshot.put(path, Files.readAllBytes(path));
        }
        return snapshot;
    }

    private static void assertFilesUnchanged(Map<Path, byte[]> snapshot) throws IOException {
        for (Map.Entry<Path, byte[]> entry : snapshot.entrySet()) {
            assertTrue(Files.isRegularFile(entry.getKey()), "中間檔應保留：" + entry.getKey());
            assertArrayEquals(entry.getValue(), Files.readAllBytes(entry.getKey()),
                    "原始區塊不應被覆寫：" + entry.getKey());
        }
    }
}
