package ir.hw1.text;

/**
 * 停用詞表。（負責：D）
 *
 * <p>從 classpath 上的 {@code stopwords.txt}（位於 src/main/resources/）讀入：
 * 一行一個詞，忽略空行與 # 開頭的註解行，全部轉小寫後存進 {@code HashSet<String>}。
 *
 * <p>提示：用 {@code StopwordList.class.getResourceAsStream("/stopwords.txt")} 開檔，
 * 再用 {@code BufferedReader} 搭配 {@code StandardCharsets.UTF_8} 逐行讀取。
 */
public class StopwordList {

    /** 讀入 resources/stopwords.txt。 */
    public static StopwordList loadDefault() {
        // TODO(D)
        throw new UnsupportedOperationException("TODO(D): StopwordList.loadDefault");
    }

    /** word 是否為停用詞（不分大小寫）。 */
    public boolean contains(String word) {
        // TODO(D)
        throw new UnsupportedOperationException("TODO(D): StopwordList.contains");
    }

    /** 停用詞數量。 */
    public int size() {
        // TODO(D)
        throw new UnsupportedOperationException("TODO(D): StopwordList.size");
    }
}
