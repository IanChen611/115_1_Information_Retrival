package ir.hw1.ext;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 加分功能：postings 的 skip pointers。（負責：A，主要功能完成後再做）
 *
 * <p>對長度 L 的 docId 列表，每隔約 √L 個位置放一個 skip pointer（課本 Sec. 2.3）。
 *
 * <p>例：docIds = [2, 4, 8, 16, 19, 23, 28, 43, 51]（L = 9，間隔 √9 = 3）
 * <pre>
 * 索引：   0   1   2   3   4   5   6   7   8
 * docId： 2   4   8  16  19  23  28  43  51
 * skip：  0 ──────────▶ 3 ──────────▶ 6        （索引 0→3、3→6；6+3=9 超出範圍，6 沒有 skip）
 * </pre>
 *
 * 不過我在做的時候覺得小於九的可以都不用跳過
 */
public class SkipList {

    private List<Integer> docIds;
    private int[] skips;

    /** @param docIds 遞增的 docId 列表 */
    public SkipList(int[] docIds) {
        // 存下 docIds
        this.docIds = Arrays.stream(docIds)
                .boxed()
                .collect(Collectors.toList());

        // 計算間隔
        int docIds_length = this.docIds.size();
        int step = Math.max(1, (int) Math.sqrt(docIds_length));

        // 建立 skips ，先填 -1 => 代表沒有 skip
        this.skips = new int[docIds_length];
        Arrays.fill(this.skips, -1);

        // 如果長度太短就不跳過了
        if(docIds_length <= 9) {
            return;
        }

        // skip
        for(int i=0; i + step < docIds_length; i += step){
            skips[i] = i + step;
        }

        /*
        * 假設是 16 個 => step = 4
        * docIds = [1, 2, 3, ..., 15, 16]
        * skips = [4, -1, -1, -1, 8, -1, ......, -1, -1, 20]
        * */
    }

    /** 第 i 個元素是否有 skip pointer。 */
    public boolean hasSkip(int i) {
        return skips[i] != -1;
    }

    /** 第 i 個元素的 skip pointer 指向的索引；需先確認 {@link #hasSkip(int)}。 */
    public int skipTarget(int i) {
        if(!hasSkip(i)) return -1;
        return skips[i];
    }

    /**
     * 兩個 postings 列表取交集（AND 查詢），利用 skip pointer 加速。
     *
     * @return 遞增的共同 docId
     */
    public static int[] intersect(SkipList a, SkipList b) {
        //  測試：用類別說明的例子 [2, 4, 8, 16, 19, 23, 28, 43, 51] 和 [3, 8, 19, 41, 51] 取交集，應得 [8, 19, 51]
        int i = 0;
        int j = 0;
        List<Integer> result = new ArrayList<>();
        while(i < a.docIds.size() && j < b.docIds.size()){
            if(Objects.equals(a.docIds.get(i), b.docIds.get(j))){
                result.add(a.docIds.get(i));
                i++;
                j++;
            }
            else if(a.docIds.get(i) < b.docIds.get(j)){
                boolean tem = true;
                while(a.hasSkip(i) && a.docIds.get(a.skipTarget(i)) <= b.docIds.get(j)){
                    tem = false;
                    i = a.skipTarget(i);
                }
                if(tem) i++;
            }
            else if(a.docIds.get(i) > b.docIds.get(j)){
                boolean tem = true;
                while(b.hasSkip(j) && b.docIds.get(b.skipTarget(j)) <= a.docIds.get(i)){
                    tem = false;
                    j = b.skipTarget(j);
                }
                if(tem) j++;
            }
        }

        return result.stream()
                .mapToInt(Integer::intValue)
                .toArray();
    }
}
