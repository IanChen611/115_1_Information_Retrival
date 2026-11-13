package ir.hw2.index;

import ir.hw1.text.TokenizerOptions;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.Closeable;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Properties;

/**
 * 查詢時使用的索引：詞典與文件資訊常駐記憶體，postings 依 lexicon 的位移從 {@code postings.txt} 隨機讀取。（工作一）
 *
 * <p>檔案由 HW1 的 {@code Indexer} 與 {@link SearchIndexBuilder} 產生。
 */
public final class IndexReader implements Closeable {

    private final TokenizerOptions options;
    private final int documentCount;
    private final int championSize;

    // 詞典：依 String.compareTo 排序（與 HW1 dictionary.txt 相同），以二分搜尋查詢
    private final String[] terms;
    private final int[] dfs;
    private final long[] postingOffsets;
    private final int[] postingLengths;
    private final long[] championOffsets;
    private final int[] championCounts;
    private final List<String> termView;

    // 文件資訊，以 docId 為索引（0 不使用）
    private final double[] tfidfNorms;
    private final double[] lncNorms;
    private final int[] lengths;
    private final double averageLength;
    private final String[] trecIds;
    private final String[] urls;

    private final FileChannel postings;
    private final FileChannel champions;

    public IndexReader(Path indexDir) throws IOException {
        Properties meta = new Properties();
        try (BufferedReader in = Files.newBufferedReader(require(indexDir, SearchIndexBuilder.META),
                StandardCharsets.UTF_8)) {
            meta.load(in);
        }
        options = new TokenizerOptions(
                Boolean.parseBoolean(meta.getProperty("caseFolding", "true")),
                Boolean.parseBoolean(meta.getProperty("removeStopwords", "true")),
                Boolean.parseBoolean(meta.getProperty("stemming", "true")));
        championSize = Integer.parseInt(meta.getProperty("championSize",
                Integer.toString(SearchIndexBuilder.DEFAULT_CHAMPION_SIZE)));

        // norms.bin
        try (DataInputStream in = new DataInputStream(new BufferedInputStream(
                Files.newInputStream(require(indexDir, SearchIndexBuilder.NORMS)), 1 << 16))) {
            documentCount = in.readInt();
            tfidfNorms = new double[documentCount + 1];
            lncNorms = new double[documentCount + 1];
            lengths = new int[documentCount + 1];
            long total = 0;
            for (int docId = 1; docId <= documentCount; docId++) {
                tfidfNorms[docId] = in.readDouble();
                lncNorms[docId] = in.readDouble();
                lengths[docId] = in.readInt();
                total += lengths[docId];
            }
            averageLength = documentCount == 0 ? 0 : (double) total / documentCount;
        }

        // docs.txt：docId <TAB> TREC-ID <TAB> URL
        trecIds = new String[documentCount + 1];
        urls = new String[documentCount + 1];
        try (BufferedReader in = Files.newBufferedReader(require(indexDir, SearchIndexBuilder.DOCS),
                StandardCharsets.UTF_8)) {
            String line;
            while ((line = in.readLine()) != null) {
                if (line.isEmpty()) {
                    continue;
                }
                String[] fields = line.split("\t", 3);
                int docId = Integer.parseInt(fields[0].strip());
                if (docId >= 1 && docId <= documentCount) {
                    trecIds[docId] = fields.length > 1 ? fields[1] : "";
                    urls[docId] = fields.length > 2 ? fields[2] : "";
                }
            }
        }

        // lexicon.txt
        List<String> termList = new ArrayList<>();
        int[] df = new int[1024];
        long[] offset = new long[1024];
        int[] length = new int[1024];
        long[] championOffset = new long[1024];
        int[] championCount = new int[1024];
        try (BufferedReader in = Files.newBufferedReader(require(indexDir, SearchIndexBuilder.LEXICON),
                StandardCharsets.UTF_8)) {
            String line;
            int count = 0;
            while ((line = in.readLine()) != null) {
                if (line.isEmpty()) {
                    continue;
                }
                String[] fields = line.split("\t");
                if (fields.length != 6) {
                    throw new IOException("lexicon.txt 格式錯誤：" + line);
                }
                if (count == df.length) {
                    int grown = count * 2;
                    df = Arrays.copyOf(df, grown);
                    offset = Arrays.copyOf(offset, grown);
                    length = Arrays.copyOf(length, grown);
                    championOffset = Arrays.copyOf(championOffset, grown);
                    championCount = Arrays.copyOf(championCount, grown);
                }
                termList.add(fields[0]);
                df[count] = Integer.parseInt(fields[1]);
                offset[count] = Long.parseLong(fields[2]);
                length[count] = Integer.parseInt(fields[3]);
                championOffset[count] = Long.parseLong(fields[4]);
                championCount[count] = Integer.parseInt(fields[5]);
                count++;
            }
            terms = termList.toArray(new String[0]);
            dfs = Arrays.copyOf(df, count);
            postingOffsets = Arrays.copyOf(offset, count);
            postingLengths = Arrays.copyOf(length, count);
            championOffsets = Arrays.copyOf(championOffset, count);
            championCounts = Arrays.copyOf(championCount, count);
        }
        for (int i = 1; i < terms.length; i++) {
            if (terms[i - 1].compareTo(terms[i]) >= 0) {
                throw new IOException("lexicon.txt 未依 term 排序：" + terms[i - 1] + " / " + terms[i]);
            }
        }
        termView = Collections.unmodifiableList(Arrays.asList(terms));

        postings = FileChannel.open(require(indexDir, SearchIndexBuilder.POSTINGS), StandardOpenOption.READ);
        champions = FileChannel.open(require(indexDir, SearchIndexBuilder.CHAMPIONS), StandardOpenOption.READ);
    }

    private static Path require(Path indexDir, String name) throws IOException {
        Path path = indexDir.resolve(name);
        if (!Files.exists(path)) {
            throw new NoSuchFileException(path.toString(), null,
                    "找不到索引檔，請先執行：java -Xmx4g -jar target/hw2.jar index --input <warc> --output " + indexDir);
        }
        return path;
    }

    /** 建索引時的 tokenizer 選項；查詢必須使用相同設定。 */
    public TokenizerOptions options() {
        return options;
    }

    /** 文件總數 N；docId 範圍為 1..N。 */
    public int documentCount() {
        return documentCount;
    }

    /** 建索引時的 champion list 長度 r。 */
    public int championSize() {
        return championSize;
    }

    public boolean contains(String term) {
        return indexOf(term) >= 0;
    }

    /** 不在詞典中時回傳 0。 */
    public int df(String term) {
        int i = indexOf(term);
        return i < 0 ? 0 : dfs[i];
    }

    /** 詞典中所有詞，依字典序排列（唯讀）。 */
    public List<String> terms() {
        return termView;
    }

    /**
     * 以 prefix 開頭的所有詞，依字典序排列。
     *
     * <p>詞典已排序，所以符合的詞是一段連續區間：以二分搜尋找到起點後往後掃即可，不需另外建 Trie。
     */
    public List<String> termsWithPrefix(String prefix) {
        int start = Arrays.binarySearch(terms, prefix);
        if (start < 0) {
            start = -start - 1;
        }
        List<String> result = new ArrayList<>();
        for (int i = start; i < terms.length && terms[i].startsWith(prefix); i++) {
            result.add(terms[i]);
        }
        return result;
    }

    /** 完整 postings（含位置）；不在詞典中時回傳 df = 0 的空清單。 */
    public PostingList postings(String term) {
        int i = indexOf(term);
        if (i < 0) {
            return PostingList.empty(term);
        }
        byte[] line = read(postings, postingOffsets[i], postingLengths[i]);
        return PostingList.parse(term, line, line.length, dfs[i]);
    }

    /** champion list：tf 最高的 r 篇，依 docId 遞增；只有 docId 與 tf。 */
    public PostingList champions(String term) {
        int i = indexOf(term);
        if (i < 0) {
            return PostingList.empty(term);
        }
        int count = championCounts[i];
        ByteBuffer buffer = ByteBuffer.wrap(read(champions, championOffsets[i], count * 8));
        int[] docIds = new int[count];
        int[] tfs = new int[count];
        for (int k = 0; k < count; k++) {
            docIds[k] = buffer.getInt();
            tfs[k] = buffer.getInt();
        }
        return PostingList.withoutPositions(term, docIds, tfs);
    }

    /** 文件向量 {@code (1 + log10 tf) × idf} 的長度。 */
    public double tfidfNorm(int docId) {
        return tfidfNorms[docId];
    }

    /** 文件向量 {@code 1 + log10 tf}（lnc）的長度。 */
    public double lncNorm(int docId) {
        return lncNorms[docId];
    }

    /** 文件長度（索引中的詞數，不含停用詞）。 */
    public int length(int docId) {
        return lengths[docId];
    }

    public double averageLength() {
        return averageLength;
    }

    /** @return {@code {TREC-ID, URL}} */
    public String[] document(int docId) {
        return new String[]{trecIds[docId], urls[docId]};
    }

    private int indexOf(String term) {
        return Arrays.binarySearch(terms, term);
    }

    private static byte[] read(FileChannel channel, long offset, int length) {
        byte[] bytes = new byte[length];
        ByteBuffer buffer = ByteBuffer.wrap(bytes);
        try {
            while (buffer.hasRemaining()) {
                int n = channel.read(buffer, offset + buffer.position());
                if (n < 0) {
                    throw new IOException("索引檔在位移 " + offset + " 處提早結束");
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return bytes;
    }

    @Override
    public void close() throws IOException {
        try {
            postings.close();
        } finally {
            champions.close();
        }
    }
}
