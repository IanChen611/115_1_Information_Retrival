package ir.hw1.text;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.LowerCaseFilter;
import org.apache.lucene.analysis.TokenFilter;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.analysis.en.PorterStemFilter;
import org.apache.lucene.analysis.standard.StandardTokenizer;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.analysis.tokenattributes.KeywordAttribute;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * {@link Tokenizer} 的實作。（HW1 負責：B；HW2 由工作一修正處理順序）
 *
 * <p>處理順序：切詞 → case folding → 停用詞過濾 → stemming。
 *
 * <p>HW2 修正：HW1 版本把 Porter stemmer 串在停用詞檢查之前，導致 {@code was} 先被
 * 詞幹化成 {@code wa} 而漏過停用詞表。這裡改成先以 {@link StopwordMarkFilter} 標記停用詞
 * （同時讓 stemmer 跳過它），輸出時再依標記丟棄，位置編號仍照原文計算。
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
            TokenStream tokenStream = analyzer.tokenStream("text", text);
            if (options.caseFolding()) {
                tokenStream = new LowerCaseFilter(tokenStream);
            }
            if (options.removeStopwords()) {
                tokenStream = new StopwordMarkFilter(tokenStream, stopwords);
            }
            if (options.stemming()) {
                // PorterStemFilter 不會處理被標成 keyword 的 token
                tokenStream = new PorterStemFilter(tokenStream);
            }
            CharTermAttribute charTermAttr = tokenStream.addAttribute(CharTermAttribute.class);
            KeywordAttribute keywordAttr = tokenStream.addAttribute(KeywordAttribute.class);

            tokenStream.reset();
            while (tokenStream.incrementToken()) {
                count++;
                if (options.removeStopwords() && keywordAttr.isKeyword()) {
                    continue;
                }
                results.add(new Token(charTermAttr.toString(), count));
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

    /** 把停用詞標成 keyword，讓後面的 stemmer 跳過、輸出時丟棄。 */
    private static final class StopwordMarkFilter extends TokenFilter {
        private final StopwordList stopwords;
        private final CharTermAttribute termAttr = addAttribute(CharTermAttribute.class);
        private final KeywordAttribute keywordAttr = addAttribute(KeywordAttribute.class);

        StopwordMarkFilter(TokenStream input, StopwordList stopwords) {
            super(input);
            this.stopwords = stopwords;
        }

        @Override
        public boolean incrementToken() throws IOException {
            if (!input.incrementToken()) {
                return false;
            }
            keywordAttr.setKeyword(stopwords.contains(termAttr.toString()));
            return true;
        }
    }
}
