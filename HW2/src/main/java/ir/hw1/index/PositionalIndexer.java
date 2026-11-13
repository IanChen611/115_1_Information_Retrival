package ir.hw1.index;

import ir.hw1.text.Token;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * {@link Indexer} 的實作。（負責：C）
 *
 * <p>以文件為單位累積區塊，達到估計記憶體門檻後寫出，再以串流合併產生最終索引。
 * 門檻是軟限制：處理一篇文件時仍需要容納該篇的 Token 與位置資訊。
 * 每次執行的中間檔放在獨立目錄，完成或失敗後都保留，不自動刪除。
 * 此類別供單一執行緒使用；finish 成功或發生 I/O 錯誤後不可重用。
 */
public class PositionalIndexer implements Indexer {

    private static final long DEFAULT_MEMORY_BUDGET = 128L * 1024 * 1024;
    private static final int DEFAULT_MERGE_FAN_IN = 32;

    private enum State { OPEN, FINISHED, FAILED }

    private final Path workRoot;
    private final long memoryBudgetBytes;
    private final int mergeFanIn;
    private final Map<String, List<Posting>> buffer = new HashMap<>();
    private final List<Path> blocks = new ArrayList<>();
    private Path workDirectory;
    private long estimatedBytes;
    private int lastDocId;
    private State state = State.OPEN;

    /** 相容 Main 的入口；中間檔預設保存在 target/index-blocks 的獨立子目錄。 */
    public PositionalIndexer() {
        this(Path.of("target", "index-blocks"), DEFAULT_MEMORY_BUDGET, DEFAULT_MERGE_FAN_IN);
    }

    public PositionalIndexer(Path workRoot, long memoryBudgetBytes) {
        this(workRoot, memoryBudgetBytes, DEFAULT_MERGE_FAN_IN);
    }

    /**
     * @param workRoot 中間檔的父目錄；第一次寫區塊時才建立獨立的 run 目錄
     * @param memoryBudgetBytes 估計記憶體的軟門檻，必須大於零
     * @param mergeFanIn 每輪同時讀取的區塊數，至少為 2
     */
    public PositionalIndexer(Path workRoot, long memoryBudgetBytes, int mergeFanIn) {
        this.workRoot = Objects.requireNonNull(workRoot, "workRoot").toAbsolutePath().normalize();
        if (memoryBudgetBytes <= 0) {
            throw new IllegalArgumentException("memoryBudgetBytes must be positive");
        }
        if (mergeFanIn < 2) {
            throw new IllegalArgumentException("mergeFanIn must be at least 2");
        }
        this.memoryBudgetBytes = memoryBudgetBytes;
        this.mergeFanIn = mergeFanIn;
    }

    @Override
    public void addDocument(int docId, List<Token> tokens) throws IOException {
        requireOpen();
        Objects.requireNonNull(tokens, "tokens");
        if (docId <= lastDocId) {
            throw new IllegalArgumentException("docId must be positive and strictly increasing: " + docId);
        }

        // 先驗證整篇，非法輸入不得改變現有索引或消耗 docId。
        int previousPosition = 0;
        for (Token token : tokens) {
            Objects.requireNonNull(token, "token");
            if (token.position() <= previousPosition) {
                throw new IllegalArgumentException("token positions must be strictly increasing");
            }
            previousPosition = token.position();
        }

        try {
            // 在整理大文件前先釋出舊區塊，減少兩者同時常駐的峰值。
            if (!buffer.isEmpty() && 8L * tokens.size() >= memoryBudgetBytes - estimatedBytes) {
                flushBlock();
            }
            Map<String, Positions> document = new HashMap<>();
            for (Token token : tokens) {
                document.computeIfAbsent(token.term(), ignored -> new Positions()).add(token.position());
            }

            long documentEstimate = 0;
            for (Map.Entry<String, Positions> entry : document.entrySet()) {
                documentEstimate += 192L + 2L * entry.getKey().length() + 8L * entry.getValue().size;
            }
            if (!buffer.isEmpty() && documentEstimate >= memoryBudgetBytes - estimatedBytes) {
                flushBlock();
            }
            for (Map.Entry<String, Positions> entry : document.entrySet()) {
                String term = entry.getKey();
                List<Posting> postings = buffer.get(term);
                if (postings == null) {
                    postings = new ArrayList<>(1);
                    buffer.put(term, postings);
                    estimatedBytes += 128L + 2L * term.length();
                }
                int[] positions = entry.getValue().toArray();
                postings.add(new Posting(docId, positions));
                estimatedBytes += 64L + 8L * positions.length;
            }
            lastDocId = docId;
            if (estimatedBytes >= memoryBudgetBytes) {
                flushBlock();
            }
        } catch (IOException e) {
            throw fail(e);
        }
    }

    @Override
    public void finish(Path outputDir) throws IOException {
        requireOpen();
        Objects.requireNonNull(outputDir, "outputDir");
        try {
            Files.createDirectories(outputDir);
            flushBlock();
            IndexMerger.finish(blocks, workDirectory, outputDir, mergeFanIn);
            state = State.FINISHED;
        } catch (IOException e) {
            throw fail(e);
        }
    }

    private void flushBlock() throws IOException {
        if (buffer.isEmpty()) {
            return;
        }
        if (workDirectory == null) {
            Files.createDirectories(workRoot);
            workDirectory = Files.createTempDirectory(workRoot, "run-");
        }
        Path block = workDirectory.resolve("block-" + blocks.size() + ".bin");
        List<String> terms = new ArrayList<>(buffer.keySet());
        terms.sort(String::compareTo);
        try (IndexBlock.Writer writer = new IndexBlock.Writer(block)) {
            for (String term : terms) {
                List<Posting> postings = buffer.get(term);
                writer.startTerm(term, postings.size());
                for (Posting posting : postings) {
                    writer.startPosting(posting.docId(), posting.tf());
                    for (int position : posting.positions()) {
                        writer.position(position);
                    }
                    writer.endPosting();
                }
                writer.endTerm();
            }
        }
        blocks.add(block);
        buffer.clear();
        estimatedBytes = 0;
    }

    private void requireOpen() {
        if (state != State.OPEN) {
            throw new IllegalStateException("indexer is " + state.name().toLowerCase(java.util.Locale.ROOT));
        }
    }

    private IOException fail(IOException cause) {
        state = State.FAILED;
        Path retainedAt = workDirectory == null ? workRoot : workDirectory;
        return new IOException("Indexing failed; intermediate files are retained under " + retainedAt, cause);
    }

    /** 避免將每個位置包裝成 Integer；產出的陣列由 Posting 獨占。 */
    private static final class Positions {
        private int[] values = new int[4];
        private int size;

        void add(int position) {
            if (size == values.length) {
                values = Arrays.copyOf(values, size + (size >> 1) + 1);
            }
            values[size++] = position;
        }

        int[] toArray() {
            return size == values.length ? values : Arrays.copyOf(values, size);
        }
    }
}
