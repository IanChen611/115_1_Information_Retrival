package ir.hw2.index;

/**
 * 一個詞的 postings：遞增的 docId、對應的 tf，以及每篇文件中的位置。（工作一）
 *
 * <p>從 HW1 的 {@code postings.txt} 讀出時，docId 與 tf 立即解析；位置只記下在原始位元組中的起點，
 * 第一次呼叫 {@link #positions()} 時才解析（自由文字與布林查詢只需要 tf，不必付出解析位置的成本）。
 *
 * <p>champion list 只保存 docId 與 tf，沒有位置資訊。
 */
public final class PostingList {

    private static final int[][] NO_POSITIONS = new int[0][];

    private final String term;
    private final int[] docIds;
    private final int[] tfs;

    // 位置的延遲解析：raw 為該詞在 postings.txt 的整行位元組，positionStarts[i] 為第 i 筆 '<' 之後的索引
    private final byte[] raw;
    private final int[] positionStarts;
    private int[][] positions;

    private PostingList(String term, int[] docIds, int[] tfs, byte[] raw, int[] positionStarts,
                        int[][] positions) {
        this.term = term;
        this.docIds = docIds;
        this.tfs = tfs;
        this.raw = raw;
        this.positionStarts = positionStarts;
        this.positions = positions;
    }

    /** 不在詞典中的詞：df = 0。 */
    public static PostingList empty(String term) {
        return new PostingList(term, new int[0], new int[0], null, null, NO_POSITIONS);
    }

    /** 只有 docId 與 tf 的 postings（champion list 使用）。 */
    static PostingList withoutPositions(String term, int[] docIds, int[] tfs) {
        return new PostingList(term, docIds, tfs, null, null, null);
    }

    /**
     * 解析 HW1 {@code postings.txt} 的一行：
     * {@code term, df: <docId, tf: <pos1, pos2, ...>; docId, tf: <...>>}
     *
     * <p>呼叫者之後不可再修改 {@code line}（位置會延遲從中解析）。
     *
     * @param line   該行的位元組（UTF-8，不含換行）
     * @param length 有效長度
     * @param df     詞典中的 df，決定要讀幾筆 posting
     */
    static PostingList parse(String term, byte[] line, int length, int df) {
        int pos = headerEnd(line, length);
        int[] docIds = new int[df];
        int[] tfs = new int[df];
        int[] starts = new int[df];
        for (int i = 0; i < df; i++) {
            // docId
            while (pos < length && !isDigit(line[pos])) {
                pos++;
            }
            int docId = 0;
            while (pos < length && isDigit(line[pos])) {
                docId = docId * 10 + (line[pos++] - '0');
            }
            // tf
            while (pos < length && !isDigit(line[pos])) {
                pos++;
            }
            int tf = 0;
            while (pos < length && isDigit(line[pos])) {
                tf = tf * 10 + (line[pos++] - '0');
            }
            // 位置清單 <...>：只記下起點，跳到結尾的 '>'
            while (pos < length && line[pos] != '<') {
                pos++;
            }
            starts[i] = ++pos;
            while (pos < length && line[pos] != '>') {
                pos++;
            }
            if (pos >= length) {
                throw new IllegalStateException("postings 格式錯誤：" + term + " 第 " + (i + 1) + " 筆");
            }
            pos++;
            docIds[i] = docId;
            tfs[i] = tf;
        }
        return new PostingList(term, docIds, tfs, line, starts, null);
    }

    /** 跳過行首的 {@code term, df: <}；term 不含空白，所以第一個 {@code ": <"} 就是表頭的結尾。 */
    private static int headerEnd(byte[] line, int length) {
        for (int i = 0; i + 2 < length; i++) {
            if (line[i] == ':' && line[i + 1] == ' ' && line[i + 2] == '<') {
                return i + 3;
            }
        }
        throw new IllegalStateException("postings 格式錯誤：找不到 \": <\"");
    }

    private static boolean isDigit(byte b) {
        return b >= '0' && b <= '9';
    }

    public String term() {
        return term;
    }

    public int df() {
        return docIds.length;
    }

    /** 遞增的 docId。 */
    public int[] docIds() {
        return docIds;
    }

    /** tfs()[i] 為 docIds()[i] 中的詞頻。 */
    public int[] tfs() {
        return tfs;
    }

    /**
     * positions()[i] 為該詞在 docIds()[i] 中的位置（遞增，從 1 起算，停用詞仍佔位置）。
     *
     * @throws IllegalStateException champion list 沒有位置資訊
     */
    public int[][] positions() {
        if (positions == null) {
            if (raw == null) {
                throw new IllegalStateException("champion list 不含位置資訊：" + term);
            }
            int[][] parsed = new int[docIds.length][];
            for (int i = 0; i < docIds.length; i++) {
                int[] list = new int[tfs[i]];
                int pos = positionStarts[i];
                for (int k = 0; k < list.length; k++) {
                    while (!isDigit(raw[pos])) {
                        pos++;
                    }
                    int value = 0;
                    while (isDigit(raw[pos])) {
                        value = value * 10 + (raw[pos++] - '0');
                    }
                    list[k] = value;
                }
                parsed[i] = list;
            }
            positions = parsed;
        }
        return positions;
    }
}
