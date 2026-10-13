package ir.hw1.text;

import java.util.List;

/**
 * {@link Tokenizer} 的實作。（負責：B）
 *
 * <p>處理順序：切詞 → case folding → 停用詞過濾 → stemming。
 * 數字、連字號、標點、撇號的規則由 B 決定，並交給 D 寫進說明文件。
 */
public class DefaultTokenizer implements Tokenizer {

    private final TokenizerOptions options;
    private final StopwordList stopwords;

    /**
     * @param options   語言處理開關
     * @param stopwords 停用詞表；{@code options.removeStopwords()} 為 false 時可為 null
     */
    public DefaultTokenizer(TokenizerOptions options, StopwordList stopwords) {
        if (options.removeStopwords() && stopwords == null) {
            throw new IllegalArgumentException("stopwords must not be null when removeStopwords is on");
        }
        this.options = options;
        this.stopwords = stopwords;
    }

    @Override
    public List<Token> tokenize(String text) {
        // TODO(B)
        throw new UnsupportedOperationException("TODO(B): DefaultTokenizer.tokenize");
    }
}
