package ir.hw1.ext;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TrieTest {

    @Test
    void containsOnlyInsertedTerms() {
        Trie trie = new Trie();
        trie.insert("cat");
        trie.insert("car");

        assertTrue(trie.contains("cat"));
        assertTrue(trie.contains("car"));
        assertFalse(trie.contains("ca"));
        assertFalse(trie.contains("dog"));
    }

    @Test
    void startsWithReturnsTermsInLexicographicOrder() {
        Trie trie = new Trie();
        trie.insert("cart");
        trie.insert("cat");
        trie.insert("car");
        trie.insert("carbon");
        trie.insert("dog");

        assertEquals(List.of("car", "carbon", "cart", "cat"), trie.startsWith("ca"));
        assertEquals(List.of("dog"), trie.startsWith("dog"));
        assertEquals(List.of("car", "carbon", "cart"), trie.startsWith("car"));
        assertEquals(List.of(), trie.startsWith("z"));
    }

    @Test
    void supportsEmptyTermAndEmptyPrefix() {
        Trie trie = new Trie();
        trie.insert("");
        trie.insert("b");
        trie.insert("a");

        assertTrue(trie.contains(""));
        assertEquals(List.of("", "a", "b"), trie.startsWith(""));
    }

    @Test
    void duplicateInsertDoesNotDuplicateResults() {
        Trie trie = new Trie();
        trie.insert("term");
        trie.insert("term");

        assertEquals(List.of("term"), trie.startsWith(""));
    }

    @Test
    void rejectsNullTermsAndPrefixes() {
        Trie trie = new Trie();

        assertThrows(NullPointerException.class, () -> trie.insert(null));
        assertThrows(NullPointerException.class, () -> trie.contains(null));
        assertThrows(NullPointerException.class, () -> trie.startsWith(null));
    }
}
