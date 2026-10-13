package ir.hw1.ext;

import org.junit.jupiter.api.Test;

import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * SkipList 的單元測試。（負責：A）執行：mvn test，或在 IntelliJ 點類別旁的綠色箭頭。
 */
class SkipListTest {

    private static final int[] TEXTBOOK = {2, 4, 8, 16, 19, 23, 28, 43, 51};

    private static int[] intersect(int[] a, int[] b) {
        return SkipList.intersect(new SkipList(a), new SkipList(b));
    }

    @Test
    void placesSkipsEverySqrtL() {
        // 16 個元素（1..16），step = √16 = 4
        SkipList skipList = new SkipList(IntStream.rangeClosed(1, 16).toArray());

        assertTrue(skipList.hasSkip(0));
        assertEquals(4, skipList.skipTarget(0));
        assertTrue(skipList.hasSkip(4));
        assertEquals(8, skipList.skipTarget(4));
        assertTrue(skipList.hasSkip(8));
        assertEquals(12, skipList.skipTarget(8));
        assertFalse(skipList.hasSkip(1));
    }

    @Test
    void lastSkipStaysInsideArray() {
        int length = 16;
        SkipList skipList = new SkipList(IntStream.rangeClosed(1, length).toArray());

        assertFalse(skipList.hasSkip(12), "12 + 4 = 16 超出範圍，不應有 skip");
        for (int i = 0; i < length; i++) {
            if (skipList.hasSkip(i)) {
                int target = skipList.skipTarget(i);
                assertTrue(target > i && target < length, "skip " + i + " -> " + target + " 超出範圍");
            }
        }
    }

    @Test
    void textbookExampleHasSkips() {
        // 類別說明的例子：9 個元素、step = 3，skip 為 0→3、3→6，6 沒有 skip
        SkipList skipList = new SkipList(TEXTBOOK);

        assertFalse(skipList.hasSkip(0), "9 個元素以內都不會被跳過");
    }

    @Test
    void intersectTextbookExample() {
        assertArrayEquals(new int[]{8, 19, 51}, intersect(TEXTBOOK, new int[]{3, 8, 19, 41, 51}));
    }

    @Test
    void intersectUsesChainedSkips() {
        // a 從 2 連跳 16 → 28（都 ≤ 30），再一步步走到 51
        assertArrayEquals(new int[]{51}, intersect(TEXTBOOK, new int[]{30, 51}));
        // 跳到的位置剛好等於目標值（16），也要被找到
        assertArrayEquals(new int[]{16, 51}, intersect(TEXTBOOK, new int[]{16, 51}));
    }

    @Test
    void intersectIsSymmetric() {
        int[] other = {3, 8, 19, 41, 51};
        assertArrayEquals(intersect(TEXTBOOK, other), intersect(other, TEXTBOOK));

        int[] a = IntStream.range(0, 100).filter(x -> x % 2 == 0).toArray();
        int[] b = IntStream.range(0, 100).filter(x -> x % 7 == 0).toArray();
        assertArrayEquals(intersect(a, b), intersect(b, a));
    }

    @Test
    void intersectWhenLastElementsMatch() {
        assertArrayEquals(new int[]{5}, intersect(new int[]{1, 5}, new int[]{5}));
        assertArrayEquals(new int[]{5}, intersect(new int[]{5}, new int[]{1, 5}));
    }

    @Test
    void intersectWithNoCommonOrEmpty() {
        assertArrayEquals(new int[]{}, intersect(new int[]{1, 3, 5}, new int[]{2, 4, 6}));
        assertArrayEquals(new int[]{}, intersect(new int[]{}, new int[]{1, 2}));
        assertArrayEquals(new int[]{}, intersect(new int[]{1, 2}, new int[]{}));
    }

    @Test
    void intersectLongLists() {
        int[] evens = IntStream.range(0, 1000).filter(x -> x % 2 == 0).toArray();
        int[] multiplesOf3 = IntStream.range(0, 1000).filter(x -> x % 3 == 0).toArray();
        int[] expected = IntStream.range(0, 1000).filter(x -> x % 6 == 0).toArray();

        int[] result = intersect(evens, multiplesOf3);
        assertEquals(167, result.length);
        assertArrayEquals(expected, result);
    }

    @Test
    void intersectSparseWithDenseList() {
        // 一邊很稀疏、一邊很密集：最能用到 skip 的情況
        int[] dense = IntStream.rangeClosed(1, 10_000).toArray();
        int[] sparse = {7, 500, 9_999};
        assertArrayEquals(sparse, intersect(dense, sparse));
        assertArrayEquals(sparse, intersect(sparse, dense));
    }
}
