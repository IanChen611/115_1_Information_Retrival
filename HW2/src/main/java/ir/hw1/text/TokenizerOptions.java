package ir.hw1.text;

/**
 * Tokenizer 的語言處理開關，由 Main 依命令列參數建立，傳入 Tokenizer 的建構子。
 *
 * @param caseFolding     轉小寫（--casefold）
 * @param removeStopwords 移除停用詞（--stopword）
 * @param stemming        Porter stemming（--stem）
 */
public record TokenizerOptions(boolean caseFolding, boolean removeStopwords, boolean stemming) {

    /** 全部關閉：保留原始詞形。 */
    public static TokenizerOptions none() {
        return new TokenizerOptions(false, false, false);
    }

    /** 全部開啟。 */
    public static TokenizerOptions all() {
        return new TokenizerOptions(true, true, true);
    }
}
