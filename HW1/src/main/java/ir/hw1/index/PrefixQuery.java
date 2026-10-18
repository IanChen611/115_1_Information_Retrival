package ir.hw1.index;

import ir.hw1.ext.Trie;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

/** Expands a term prefix using the index vocabulary and prints the matching postings. */
public final class PrefixQuery {

    private PrefixQuery() { }

    /**
     * Writes the complete postings lines for all terms that start with {@code prefix}.
     * The output order follows the lexicographic order of the index terms.
     */
    public static void writeMatches(Path indexDirectory, String prefix, Writer output) throws IOException {
        Objects.requireNonNull(indexDirectory, "indexDirectory");
        Objects.requireNonNull(prefix, "prefix");
        Objects.requireNonNull(output, "output");

        Trie vocabulary = new Trie();
        Path dictionaryPath = indexDirectory.resolve("dictionary.txt");
        try (BufferedReader dictionary = Files.newBufferedReader(dictionaryPath, StandardCharsets.UTF_8)) {
            String line;
            int lineNumber = 0;
            while ((line = dictionary.readLine()) != null) {
                lineNumber++;
                int separator = line.indexOf('\t');
                if (separator < 0) {
                    throw new IOException("Malformed dictionary entry at line " + lineNumber + ": missing tab");
                }
                vocabulary.insert(line.substring(0, separator));
            }
        }

        List<String> matchingTerms = vocabulary.startsWith(prefix);
        if (matchingTerms.isEmpty()) {
            return;
        }

        Path postingsPath = indexDirectory.resolve("postings.txt");
        try (BufferedReader postings = Files.newBufferedReader(postingsPath, StandardCharsets.UTF_8)) {
            String line;
            int termIndex = 0;
            int lineNumber = 0;
            while ((line = postings.readLine()) != null) {
                lineNumber++;
                int separator = line.indexOf(", ");
                if (separator < 0) {
                    throw new IOException("Malformed postings entry at line " + lineNumber + ": missing term separator");
                }
                String term = line.substring(0, separator);
                while (termIndex < matchingTerms.size()
                        && matchingTerms.get(termIndex).compareTo(term) < 0) {
                    termIndex++;
                }
                if (termIndex < matchingTerms.size() && matchingTerms.get(termIndex).equals(term)) {
                    output.write(line);
                    output.write(System.lineSeparator());
                    termIndex++;
                    if (termIndex == matchingTerms.size()) {
                        break;
                    }
                }
            }
        }
    }
}
