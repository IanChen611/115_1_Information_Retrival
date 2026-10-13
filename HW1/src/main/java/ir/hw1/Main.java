package ir.hw1;

import ir.hw1.index.Indexer;
import ir.hw1.index.PositionalIndexer;
import ir.hw1.parser.Document;
import ir.hw1.parser.DocumentSource;
import ir.hw1.parser.WarcReader;
import ir.hw1.text.DefaultTokenizer;
import ir.hw1.text.StopwordList;
import ir.hw1.text.Token;
import ir.hw1.text.Tokenizer;
import ir.hw1.text.TokenizerOptions;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * 程式進入點：解析命令列參數，串接 WarcReader → Tokenizer → Indexer。（負責：A）
 *
 * <pre>
 * java -Xmx4g -jar target/hw1.jar --input data/00.warc.gz [--input ...] --output out/
 *      [--casefold] [--stopword] [--stem]
 * </pre>
 *
 * 輸出（皆在 --output 目錄下）：
 * <ul>
 *   <li>docs.txt：docId、TREC-ID、URL 對照表（tab 分隔）</li>
 *   <li>dictionary.txt、postings.txt：見 {@link Indexer#finish(Path)}</li>
 * </ul>
 */
public final class Main {

    public static void main(String[] args) throws IOException {
        List<Path> inputs = new ArrayList<>();
        Path output = Path.of("out");
        boolean caseFolding = false;
        boolean removeStopwords = false;
        boolean stemming = false;

        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--input" -> inputs.add(Path.of(requireValue(args, ++i)));
                case "--output" -> output = Path.of(requireValue(args, ++i));
                case "--casefold" -> caseFolding = true;
                case "--stopword" -> removeStopwords = true;
                case "--stem" -> stemming = true;
                default -> {
                    System.err.println("Unknown argument: " + args[i]);
                    printUsage();
                    System.exit(1);
                }
            }
        }
        if (inputs.isEmpty()) {
            printUsage();
            System.exit(1);
        }

        TokenizerOptions options = new TokenizerOptions(caseFolding, removeStopwords, stemming);
        StopwordList stopwords = removeStopwords ? StopwordList.loadDefault() : null;
        Tokenizer tokenizer = new DefaultTokenizer(options, stopwords);
        Indexer indexer = new PositionalIndexer();

        Files.createDirectories(output);
        long start = System.currentTimeMillis();
        int count = 0;

        try (DocumentSource source = new WarcReader(inputs);
             BufferedWriter docs = Files.newBufferedWriter(output.resolve("docs.txt"), StandardCharsets.UTF_8)) {
            while (source.hasNext()) {
                Document doc = source.next();
                docs.write(doc.docId() + "\t" + doc.trecId() + "\t" + doc.url());
                docs.newLine();

                List<Token> tokens = tokenizer.tokenize(doc.text());
                indexer.addDocument(doc.docId(), tokens);

                if (++count % 1000 == 0) {
                    System.out.printf("Indexed %d documents%n", count);
                }
            }
        }

        indexer.finish(output);
        System.out.printf("Done: %d documents in %.1f s, output in %s%n",
                count, (System.currentTimeMillis() - start) / 1000.0, output.toAbsolutePath());
    }

    private static String requireValue(String[] args, int i) {
        if (i >= args.length) {
            System.err.println("Missing value for " + args[i - 1]);
            printUsage();
            System.exit(1);
        }
        return args[i];
    }

    private static void printUsage() {
        System.err.println("""
                Usage: java -jar hw1.jar --input <file.warc.gz> [--input ...] [--output <dir>]
                                         [--casefold] [--stopword] [--stem]""");
    }
}
