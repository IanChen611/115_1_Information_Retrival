package ir.hw1.ext;

/**
 * 加分功能：postings 的 skip pointers。（負責：A，主要功能完成後再做）
 *
 * <p>對長度 L 的 docId 列表，每隔約 √L 個位置放一個 skip pointer（課本 Sec. 2.3）。
 */
public class SkipList {

    /** @param docIds 遞增的 docId 列表 */
    public SkipList(int[] docIds) {
        // TODO(A)
    }

    /** 第 i 個元素是否有 skip pointer。 */
    public boolean hasSkip(int i) {
        // TODO(A)
        throw new UnsupportedOperationException("TODO(A): SkipList.hasSkip");
    }

    /** 第 i 個元素的 skip pointer 指向的索引；需先確認 {@link #hasSkip(int)}。 */
    public int skipTarget(int i) {
        // TODO(A)
        throw new UnsupportedOperationException("TODO(A): SkipList.skipTarget");
    }

    /**
     * 兩個 postings 列表取交集（AND 查詢），利用 skip pointer 加速。
     *
     * @return 遞增的共同 docId
     */
    public static int[] intersect(SkipList a, SkipList b) {
        // TODO(A)
        throw new UnsupportedOperationException("TODO(A): SkipList.intersect");
    }
}
