package ir.hw1.text;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class StopwordListTest {

    @Test
    void loadDefaultLoadsStopwordsSuccessfully() {
        StopwordList stopwords = StopwordList.loadDefault();
        assertNotNull(stopwords);
        assertTrue(stopwords.size() > 0, "Stopwords list should not be empty");
    }

    @Test
    void containsIsCaseInsensitive() {
        StopwordList stopwords = StopwordList.loadDefault();
        assertTrue(stopwords.contains("to"));
        assertTrue(stopwords.contains("TO"));
        assertTrue(stopwords.contains("To"));
        assertTrue(stopwords.contains("the"));
        assertTrue(stopwords.contains("THE"));
    }

    @Test
    void nonStopwordsReturnFalse() {
        StopwordList stopwords = StopwordList.loadDefault();
        assertFalse(stopwords.contains("retrieval"));
        assertFalse(stopwords.contains("algorithm"));
        assertFalse(stopwords.contains(null));
    }
}
