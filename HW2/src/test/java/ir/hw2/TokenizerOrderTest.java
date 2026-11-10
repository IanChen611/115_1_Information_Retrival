package ir.hw2;

import ir.hw1.text.DefaultTokenizer;
import ir.hw1.text.StopwordList;
import ir.hw1.text.Token;
import ir.hw1.text.TokenizerOptions;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** HW2 對 tokenizer 處理順序的修正：停用詞要在 stemming 之前過濾。（工作四） */
class TokenizerOrderTest {

    @Test
    void stopwordsAreRemovedBeforeStemming() {
        DefaultTokenizer tokenizer = new DefaultTokenizer(TokenizerOptions.all(), StopwordList.loadDefault());
        // HW1 版本會留下 thi、wa（this、was 先被詞幹化而漏過停用詞表）
        List<Token> tokens = tokenizer.tokenize("This was the Running dogs");
        assertEquals(List.of(new Token("run", 4), new Token("dog", 5)), tokens);
    }
}
