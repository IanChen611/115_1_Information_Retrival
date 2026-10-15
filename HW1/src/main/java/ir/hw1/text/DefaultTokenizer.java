package ir.hw1.text;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.LowerCaseFilter;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.analysis.en.PorterStemFilter;
import org.apache.lucene.analysis.standard.StandardTokenizer;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * {@link Tokenizer} 的實作。（負責：B）
 *
 * <p>處理順序：切詞 → case folding → 停用詞過濾 → stemming。
 * 數字、連字號、標點、撇號的規則由 B 決定，並交給 D 寫進說明文件。
 */
public class DefaultTokenizer implements ir.hw1.text.Tokenizer {

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
        List<Token> results = new ArrayList<>();
        int count = 0;

        try (Analyzer analyzer = new MyAnalyzer()) {
            // 從文字中獲取 TokenStream（欄位名稱 "text" 可自訂，不影響單純斷詞）
            TokenStream tokenStream = analyzer.tokenStream("text", text);
            if (options.caseFolding()) {
                tokenStream = new LowerCaseFilter(tokenStream);
            }
            if (options.stemming()) {
                tokenStream = new PorterStemFilter(tokenStream);
            }
            // 獲取文字屬性（用於讀取斷詞後的字詞）
            CharTermAttribute charTermAttr = tokenStream.addAttribute(CharTermAttribute.class);

            tokenStream.reset();

            // 迴圈遍歷所有 Token
            while (tokenStream.incrementToken()) {
                count++;
                String word = charTermAttr.toString();
                if (options.removeStopwords() && stopwords.contains(word)) {
                    continue;
                }
                results.add(new Token(word, count));
            }

            tokenStream.end();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return results;
    }

    public static class MyAnalyzer extends Analyzer {
        @Override
        protected TokenStreamComponents createComponents(String fieldName) {
            Tokenizer source = new StandardTokenizer();
            return new TokenStreamComponents(source, source);
        }
    }
}
