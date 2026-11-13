package ir.hw1.index;

/**
 * 一個 term 在一篇文件中的所有出現位置。（負責：C，可依需要調整內部表示）
 *
 * @param docId     文件編號
 * @param positions 遞增的位置陣列（從 1 開始）
 */
public record Posting(int docId, int[] positions) {

    /** term frequency：此 term 在該文件出現的次數。 */
    public int tf() {
        return positions.length;
    }
}
