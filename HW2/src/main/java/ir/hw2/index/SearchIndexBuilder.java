package ir.hw2.index;

import ir.hw1.text.TokenizerOptions;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Locale;
import java.util.Properties;

/**
 * 在 HW1 輸出（{@code dictionary.txt}、{@code postings.txt}、{@code docs.txt}）之外，
 * 額外產生查詢需要的檔案；HW1 的檔案格式完全不變。（工作一）
 *
 * <ul>
 *   <li><b>lexicon.txt</b>：{@code term<TAB>df<TAB>postings 位移<TAB>postings 長度<TAB>champion 位移<TAB>champion 筆數}，
 *       依 term 排序；位移單位為位元組，查詢時直接跳讀 {@code postings.txt}</li>
 *   <li><b>norms.bin</b>：{@code int N}，接著 docId = 1..N 各一筆
 *       {@code double tfidfNorm, double lncNorm, int length}</li>
 *   <li><b>champions.bin</b>：每個詞 tf 最高的 r 篇，依 docId 遞增存成 {@code int docId, int tf}</li>
 *   <li><b>meta.properties</b>：tokenizer 選項、文件數、平均文件長度、champion 大小</li>
 * </ul>
 */
public final class SearchIndexBuilder {

    /** champion list 預設長度 r。 */
    public static final int DEFAULT_CHAMPION_SIZE = 50;

    static final String DICTIONARY = "dictionary.txt";
    static final String POSTINGS = "postings.txt";
    static final String DOCS = "docs.txt";
    static final String LEXICON = "lexicon.txt";
    static final String NORMS = "norms.bin";
    static final String CHAMPIONS = "champions.bin";
    static final String META = "meta.properties";

    private SearchIndexBuilder() {
    }

    /**
     * @param indexDir     已含 HW1 輸出與 docs.txt 的目錄
     * @param options      建索引時使用的 tokenizer 選項（查詢時沿用）
     * @param championSize 每個詞的 champion list 長度 r
     */
    public static void build(Path indexDir, TokenizerOptions options, int championSize) throws IOException {
        if (championSize < 1) {
            throw new IllegalArgumentException("championSize must be >= 1");
        }
        int n = documentCount(indexDir.resolve(DOCS));

        double[] tfidfSquares = new double[n + 1];
        double[] lncSquares = new double[n + 1];
        int[] lengths = new int[n + 1];
        long termCount = 0;

        try (BufferedReader dictionary = Files.newBufferedReader(indexDir.resolve(DICTIONARY), StandardCharsets.UTF_8);
             LineReader postings = new LineReader(Files.newInputStream(indexDir.resolve(POSTINGS)));
             BufferedWriter lexicon = Files.newBufferedWriter(indexDir.resolve(LEXICON), StandardCharsets.UTF_8);
             CountingOutputStream championBytes = new CountingOutputStream(
                     new BufferedOutputStream(Files.newOutputStream(indexDir.resolve(CHAMPIONS)), 1 << 16));
             DataOutputStream champions = new DataOutputStream(championBytes)) {

            String entry;
            while ((entry = dictionary.readLine()) != null) {
                if (entry.isEmpty()) {
                    continue;
                }
                int tab = entry.indexOf('\t');
                String term = entry.substring(0, tab);
                int df = Integer.parseInt(entry.substring(tab + 1).strip());

                int length = postings.readLine();
                if (length < 0) {
                    throw new IOException(POSTINGS + " 的行數比 " + DICTIONARY + " 少（缺少 " + term + "）");
                }
                long offset = postings.lineStart();
                byte[] line = postings.buffer();
                if (!startsWithTerm(line, length, term)) {
                    throw new IOException(POSTINGS + " 與 " + DICTIONARY + " 的順序不一致，位移 "
                            + offset + " 預期為 " + term);
                }
                PostingList list = PostingList.parse(term, line, length, df);
                int[] docIds = list.docIds();
                int[] tfs = list.tfs();

                // 文件向量長度：w = (1 + log10 tf) × log10(N / df)
                double idf = Math.log10((double) n / df);
                for (int i = 0; i < df; i++) {
                    int docId = docIds[i];
                    if (docId < 1 || docId > n) {
                        throw new IOException("docId " + docId + " 超出 docs.txt 的範圍 1.." + n);
                    }
                    double logTf = 1 + Math.log10(tfs[i]);
                    double weight = logTf * idf;
                    tfidfSquares[docId] += weight * weight;
                    lncSquares[docId] += logTf * logTf;
                    lengths[docId] += tfs[i];
                }

                // champion list：tf 最高的 r 篇（同 tf 取 docId 小者），再依 docId 排回遞增
                long championOffset = championBytes.count();
                int[] chosen = champions(docIds, tfs, championSize);
                for (int i : chosen) {
                    champions.writeInt(docIds[i]);
                    champions.writeInt(tfs[i]);
                }

                lexicon.write(term);
                lexicon.write('\t');
                lexicon.write(Integer.toString(df));
                lexicon.write('\t');
                lexicon.write(Long.toString(offset));
                lexicon.write('\t');
                lexicon.write(Integer.toString(length));
                lexicon.write('\t');
                lexicon.write(Long.toString(championOffset));
                lexicon.write('\t');
                lexicon.write(Integer.toString(chosen.length));
                lexicon.write('\n');
                termCount++;
            }
            if (postings.readLine() >= 0) {
                throw new IOException(POSTINGS + " 的行數比 " + DICTIONARY + " 多");
            }
        }

        long totalLength = 0;
        try (DataOutputStream norms = new DataOutputStream(new BufferedOutputStream(
                Files.newOutputStream(indexDir.resolve(NORMS)), 1 << 16))) {
            norms.writeInt(n);
            for (int docId = 1; docId <= n; docId++) {
                norms.writeDouble(Math.sqrt(tfidfSquares[docId]));
                norms.writeDouble(Math.sqrt(lncSquares[docId]));
                norms.writeInt(lengths[docId]);
                totalLength += lengths[docId];
            }
        }

        Properties meta = new Properties();
        meta.setProperty("caseFolding", Boolean.toString(options.caseFolding()));
        meta.setProperty("removeStopwords", Boolean.toString(options.removeStopwords()));
        meta.setProperty("stemming", Boolean.toString(options.stemming()));
        meta.setProperty("documentCount", Integer.toString(n));
        meta.setProperty("termCount", Long.toString(termCount));
        meta.setProperty("averageLength", String.format(Locale.ROOT, "%.6f", n == 0 ? 0.0 : (double) totalLength / n));
        meta.setProperty("championSize", Integer.toString(championSize));
        try (BufferedWriter out = Files.newBufferedWriter(indexDir.resolve(META), StandardCharsets.UTF_8)) {
            meta.store(out, "IR HW2 search index");
        }
    }

    /** docs.txt 中最大的 docId（WarcReader 保證 docId 從 1 起連續編號）。 */
    private static int documentCount(Path docs) throws IOException {
        int max = 0;
        try (BufferedReader reader = Files.newBufferedReader(docs, StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isEmpty()) {
                    continue;
                }
                int tab = line.indexOf('\t');
                max = Math.max(max, Integer.parseInt(tab < 0 ? line.strip() : line.substring(0, tab)));
            }
        }
        return max;
    }

    /** 回傳被選為 champion 的 posting 索引（遞增，也就是 docId 遞增）。 */
    static int[] champions(int[] docIds, int[] tfs, int size) {
        int df = docIds.length;
        if (df <= size) {
            int[] all = new int[df];
            for (int i = 0; i < df; i++) {
                all[i] = i;
            }
            return all;
        }
        Integer[] order = new Integer[df];
        for (int i = 0; i < df; i++) {
            order[i] = i;
        }
        // 索引本身就依 docId 遞增，所以同 tf 時比較索引即可
        Arrays.sort(order, (a, b) -> tfs[a] != tfs[b] ? Integer.compare(tfs[b], tfs[a]) : Integer.compare(a, b));
        int[] chosen = new int[size];
        for (int i = 0; i < size; i++) {
            chosen[i] = order[i];
        }
        Arrays.sort(chosen);
        return chosen;
    }

    private static boolean startsWithTerm(byte[] line, int length, String term) {
        byte[] bytes = term.getBytes(StandardCharsets.UTF_8);
        if (length < bytes.length + 2) {
            return false;
        }
        for (int i = 0; i < bytes.length; i++) {
            if (line[i] != bytes[i]) {
                return false;
            }
        }
        return line[bytes.length] == ',' && line[bytes.length + 1] == ' ';
    }

    /** 以位元組逐行讀取並記錄每行的起始位移；同時接受 LF 與 CRLF。 */
    private static final class LineReader implements AutoCloseable {
        private final InputStream in;
        private byte[] buffer = new byte[1 << 16];
        private long position;
        private long lineStart;

        LineReader(InputStream in) {
            this.in = new BufferedInputStream(in, 1 << 20);
        }

        /** @return 該行長度（不含換行字元）；檔案結束時回傳 -1 */
        int readLine() throws IOException {
            lineStart = position;
            int length = 0;
            int b;
            while ((b = in.read()) != -1) {
                position++;
                if (b == '\n') {
                    break;
                }
                if (length == buffer.length) {
                    buffer = Arrays.copyOf(buffer, buffer.length * 2);
                }
                buffer[length++] = (byte) b;
            }
            if (b == -1 && length == 0) {
                return -1;
            }
            if (length > 0 && buffer[length - 1] == '\r') {
                length--;
            }
            return length;
        }

        long lineStart() {
            return lineStart;
        }

        /** 目前這行的位元組；下次 readLine 前有效。 */
        byte[] buffer() {
            return buffer;
        }

        @Override
        public void close() throws IOException {
            in.close();
        }
    }

    /** 計算已寫出的位元組數，用來記錄 champions.bin 的位移。 */
    private static final class CountingOutputStream extends OutputStream {
        private final OutputStream out;
        private long count;

        CountingOutputStream(OutputStream out) {
            this.out = out;
        }

        long count() {
            return count;
        }

        @Override
        public void write(int b) throws IOException {
            out.write(b);
            count++;
        }

        @Override
        public void write(byte[] b, int off, int len) throws IOException {
            out.write(b, off, len);
            count += len;
        }

        @Override
        public void flush() throws IOException {
            out.flush();
        }

        @Override
        public void close() throws IOException {
            out.close();
        }
    }
}
