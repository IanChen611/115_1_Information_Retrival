package ir.hw1.index;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PrefixQueryTest {

    @TempDir
    Path indexDirectory;

    @Test
    void writesPostingsForPrefixMatchesInTermOrder() throws Exception {
        Files.write(indexDirectory.resolve("dictionary.txt"), List.of(
                "car\t2",
                "carbon\t1",
                "cat\t3",
                "dog\t1"
        ), StandardCharsets.UTF_8);
        Files.write(indexDirectory.resolve("postings.txt"), List.of(
                "car, 2: <1, 1: <2>; 3, 1: <4>>",
                "carbon, 1: <2, 1: <7>>",
                "cat, 3: <1, 1: <1>; 2, 1: <3>; 3, 1: <5>>",
                "dog, 1: <4, 1: <2>>"
        ), StandardCharsets.UTF_8);

        StringWriter output = new StringWriter();
        PrefixQuery.writeMatches(indexDirectory, "ca", output);

        assertEquals(String.join(System.lineSeparator(),
                "car, 2: <1, 1: <2>; 3, 1: <4>>",
                "carbon, 1: <2, 1: <7>>",
                "cat, 3: <1, 1: <1>; 2, 1: <3>; 3, 1: <5>>",
                ""), output.toString());
    }

    @Test
    void writesNothingWhenNoTermMatches() throws Exception {
        Files.write(indexDirectory.resolve("dictionary.txt"), List.of("cat\t1"), StandardCharsets.UTF_8);
        StringWriter output = new StringWriter();

        PrefixQuery.writeMatches(indexDirectory, "dog", output);

        assertEquals("", output.toString());
    }
}
