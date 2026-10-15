package ir.hw1.text;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DefaultTokenizerTest {

    @Test
    void preservesTokenCaseWhenCaseFoldingIsDisabled() {
        DefaultTokenizer tokenizer = new DefaultTokenizer(TokenizerOptions.none(), null);

        assertEquals(
                List.of(new Token("Hello", 1), new Token("WORLD", 2)),
                tokenizer.tokenize("Hello, WORLD!")
        );
    }

    @Test
    void foldsTokenCaseWhenEnabled() {
        DefaultTokenizer tokenizer = new DefaultTokenizer(
                new TokenizerOptions(true, false, false),
                null
        );

        assertEquals(
                List.of(new Token("hello", 1), new Token("world", 2)),
                tokenizer.tokenize("Hello, WORLD!")
        );
    }

    @Test
    void stemsTokensWhenEnabled() {
        DefaultTokenizer tokenizer = new DefaultTokenizer(
                new TokenizerOptions(false, false, true),
                null
        );

        assertEquals(
                List.of(new Token("run", 1), new Token("jump", 2)),
                tokenizer.tokenize("running jumped")
        );
    }

    @Test
    void assignsPositionsStartingAtOneForEachToken() {
        DefaultTokenizer tokenizer = new DefaultTokenizer(TokenizerOptions.none(), null);

        assertEquals(
                List.of(new Token("one", 1), new Token("two", 2), new Token("three", 3)),
                tokenizer.tokenize("one, two; three.")
        );
    }

    @Test
    void returnsEmptyListForTextWithoutTokens() {
        DefaultTokenizer tokenizer = new DefaultTokenizer(TokenizerOptions.none(), null);

        assertEquals(List.of(), tokenizer.tokenize("... --- !!!"));
    }

    @Test
    void canBeReusedAcrossDocumentsWithoutCarryingPositions() {
        DefaultTokenizer tokenizer = new DefaultTokenizer(TokenizerOptions.none(), null);

        assertEquals(List.of(new Token("first", 1), new Token("document", 2)),
                tokenizer.tokenize("first document"));
        assertEquals(List.of(new Token("second", 1)), tokenizer.tokenize("second"));
    }

    @Test
    void removeStopwordWhenStopwordRemovalIsEnabled() {
        DefaultTokenizer tokenizer = new DefaultTokenizer(
                new TokenizerOptions(false, true, false),
                StopwordList.loadDefault()
        );

        assertEquals(List.of(new Token("go", 1), new Token("bed", 3)),
                tokenizer.tokenize("go to bed"));
    }
}
