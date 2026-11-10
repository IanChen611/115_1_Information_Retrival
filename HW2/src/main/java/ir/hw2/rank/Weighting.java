package ir.hw2.rank;

import java.util.Locale;

/**
 * 詞權重與相似度的計算方式。（工作三）
 */
public enum Weighting {

    /**
     * 作業指定：文件與查詢都用 {@code (1 + log10 tf) × log10(N/df)}，再取餘弦相似度。
     */
    TFIDF("tfidf"),

    /**
     * 課本 Ch.6 的 lnc.ltc：文件用 {@code 1 + log10 tf}（不乘 idf），查詢用 {@code (1 + log10 tf) × idf}，
     * 兩邊各自做餘弦正規化。
     */
    LNC_LTC("lnc.ltc"),

    /** Okapi BM25（k1 = 1.2、b = 0.75）；分數不是餘弦值，沒有上限 1。 */
    BM25("bm25");

    private final String label;

    Weighting(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    public static Weighting parse(String text) {
        String wanted = text.toLowerCase(Locale.ROOT);
        for (Weighting weighting : values()) {
            if (weighting.label.equals(wanted)) {
                return weighting;
            }
        }
        throw new IllegalArgumentException("未知的權重方式：" + text + "（可用：tfidf、lnc.ltc、bm25）");
    }
}
