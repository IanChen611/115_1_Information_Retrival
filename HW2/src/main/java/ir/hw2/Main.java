package ir.hw2;

import ir.hw1.index.Indexer;
import ir.hw1.index.PositionalIndexer;
import ir.hw1.parser.Document;
import ir.hw1.parser.DocumentSource;
import ir.hw1.parser.WarcReader;
import ir.hw1.text.DefaultTokenizer;
import ir.hw1.text.StopwordList;
import ir.hw1.text.Tokenizer;
import ir.hw1.text.TokenizerOptions;
import ir.hw2.index.SearchIndexBuilder;
import ir.hw2.query.QuerySyntaxException;
import ir.hw2.rank.Ranker;
import ir.hw2.rank.Weighting;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

/**
 * 程式進入點。（工作一）
 *
 * <pre>
 * java -Xmx4g -jar target/hw2.jar index  --input data/10.warc.gz [--input ...] [--output index/]
 * java -jar target/hw2.jar search [--index-dir index/] [選項] hong kong business     單次查詢
 * java -jar target/hw2.jar search [--index-dir index/] [選項]                         互動模式 cmd&gt;
 * java -jar target/hw2.jar search [--index-dir index/] [選項] --queries q.txt         批次查詢
 *
 * 選項：--weighting tfidf|lnc.ltc|bm25   --top-k N（0 = 全部）   --champion   --verbose   --output 檔案
 * </pre>
 *
 * 結果每行為 {@code docId <TAB> score}；提示訊息一律寫到 stderr。
 */
public final class Main {

    private Main() {
    }

    public static void main(String[] args) throws IOException {
        if (args.length == 0) {
            usage();
        }
        String[] rest = java.util.Arrays.copyOfRange(args, 1, args.length);
        switch (args[0]) {
            case "index" -> index(rest);
            case "search" -> search(rest);
            default -> usage();
        }
    }

    private static void index(String[] args) throws IOException {
        List<Path> inputs = new ArrayList<>();
        Path output = Path.of("index");
        int championSize = SearchIndexBuilder.DEFAULT_CHAMPION_SIZE;
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--input" -> inputs.add(Path.of(value(args, ++i)));
                case "--output" -> output = Path.of(value(args, ++i));
                case "--champion-size" -> championSize = Integer.parseInt(value(args, ++i));
                default -> usage();
            }
        }
        if (inputs.isEmpty() || championSize < 1) {
            usage();
        }

        // HW2 固定使用 case folding + 停用詞 + stemming；選項會寫進 meta.properties 供查詢時沿用
        TokenizerOptions options = TokenizerOptions.all();
        Tokenizer tokenizer = new DefaultTokenizer(options, StopwordList.loadDefault());
        Files.createDirectories(output);
        Path work = output.resolve("blocks-tmp");
        Indexer indexer = new PositionalIndexer(work, 128L * 1024 * 1024);

        long start = System.currentTimeMillis();
        int count = 0;
        try (DocumentSource source = new WarcReader(inputs);
             BufferedWriter docs = Files.newBufferedWriter(output.resolve("docs.txt"), StandardCharsets.UTF_8)) {
            while (source.hasNext()) {
                Document doc = source.next();
                docs.write(doc.docId() + "\t" + doc.trecId() + "\t" + doc.url());
                docs.newLine();
                indexer.addDocument(doc.docId(), tokenizer.tokenize(doc.text()));
                if (++count % 5000 == 0) {
                    System.err.printf("Indexed %d documents%n", count);
                }
            }
        }
        indexer.finish(output);
        deleteRecursively(work);
        System.err.printf("Inverted index: %d documents in %.1f s%n", count,
                (System.currentTimeMillis() - start) / 1000.0);

        long prepare = System.currentTimeMillis();
        SearchIndexBuilder.build(output, options, championSize);
        System.err.printf("Search files (lexicon, norms, champions): %.1f s, output in %s%n",
                (System.currentTimeMillis() - prepare) / 1000.0, output.toAbsolutePath());
    }

    private static void search(String[] args) throws IOException {
        Path indexDir = Path.of("index");
        Weighting weighting = Weighting.TFIDF;
        int topK = 10;
        boolean champion = false;
        boolean verbose = false;
        Path queries = null;
        Path output = null;
        List<String> words = new ArrayList<>();
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--index-dir" -> indexDir = Path.of(value(args, ++i));
                case "--weighting" -> weighting = Weighting.parse(value(args, ++i));
                case "--top-k" -> topK = Integer.parseInt(value(args, ++i));
                case "--champion" -> champion = true;
                case "--verbose" -> verbose = true;
                case "--queries" -> queries = Path.of(value(args, ++i));
                case "--output" -> output = Path.of(value(args, ++i));
                default -> words.add(args[i]);
            }
        }

        // 直接用 JVM 的主控台串流，中文提示才會以主控台的編碼輸出；--output 檔案固定 UTF-8
        PrintStream console = System.out;
        PrintStream info = System.err;
        if (output != null && output.toAbsolutePath().getParent() != null) {
            Files.createDirectories(output.toAbsolutePath().getParent());
        }
        try (SearchEngine engine = new SearchEngine(indexDir);
             PrintWriter file = output == null ? null
                     : new PrintWriter(Files.newBufferedWriter(output, StandardCharsets.UTF_8))) {
            Session session = new Session(engine, weighting, topK, champion, verbose, console, info, file);
            if (queries != null) {
                for (String line : Files.readAllLines(queries, StandardCharsets.UTF_8)) {
                    if (!line.isBlank() && !line.startsWith("#")) {
                        session.run(line.strip(), true);
                    }
                }
            } else if (!words.isEmpty()) {
                session.run(String.join(" ", words), false);
            } else {
                info.printf("%d documents loaded. Operators: AND OR NOT ( ) \"phrase\" a /k b prefix*  (exit to quit)%n",
                        engine.index().documentCount());
                BufferedReader in = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
                while (true) {
                    console.print("cmd> ");
                    console.flush();
                    String line = in.readLine();
                    if (line == null || line.strip().equalsIgnoreCase("exit")) {
                        break;
                    }
                    if (!line.isBlank()) {
                        session.run(line.strip(), false);
                    }
                }
            }
        }
    }

    /** 執行查詢並輸出結果。 */
    private record Session(SearchEngine engine, Weighting weighting, int topK, boolean champion, boolean verbose,
                           PrintStream console, PrintStream info, PrintWriter file) {

        void run(String query, boolean header) {
            long start = System.nanoTime();
            SearchEngine.Result result;
            try {
                result = engine.search(query, weighting, topK, champion);
            } catch (QuerySyntaxException e) {
                info.println("查詢語法錯誤：" + e.getMessage());
                return;
            }
            double millis = (System.nanoTime() - start) / 1e6;
            if (header) {
                emit("# Query: " + query);
            }
            for (String note : result.notes()) {
                info.println("# " + note);
            }
            for (Ranker.Hit hit : result.hits()) {
                String row = hit.docId() + "\t" + String.format(Locale.ROOT, "%.6f", hit.score());
                if (verbose) {
                    String[] doc = engine.index().document(hit.docId());
                    row += "\t" + doc[0] + "\t" + doc[1];
                }
                emit(row);
            }
            info.printf(Locale.ROOT, "# %d matching documents, showing %d (%s%s, %.1f ms)%n", result.matched(),
                    result.hits().size(), weighting.label(), champion ? ", champion lists" : "", millis);
            if (file != null) {
                file.flush();
            }
        }

        private void emit(String row) {
            console.println(row);
            if (file != null) {
                file.println(row);
            }
        }
    }

    private static void deleteRecursively(Path root) throws IOException {
        if (!Files.exists(root)) {
            return;
        }
        try (Stream<Path> paths = Files.walk(root)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                Files.delete(path);
            }
        }
    }

    private static String value(String[] args, int i) {
        if (i >= args.length) {
            usage();
        }
        return args[i];
    }

    private static void usage() {
        System.err.println("""
                Usage:
                  java -Xmx4g -jar hw2.jar index --input <file.warc.gz> [--input ...] [--output <dir>]
                                                 [--champion-size <r>]
                  java -jar hw2.jar search [--index-dir <dir>] [options] <query words...>
                  java -jar hw2.jar search [--index-dir <dir>] [options]                    (interactive)
                  java -jar hw2.jar search [--index-dir <dir>] [options] --queries <file>   (one query per line)
                Options:
                  --weighting tfidf|lnc.ltc|bm25   --top-k <N> (0 = all)   --champion   --verbose   --output <file>
                Query syntax:
                  hong kong business            free text (ranked, any term)
                  open source AND linux         AND / OR / NOT / parentheses (operators in upper case)
                  "open source"                 phrase
                  search /3 engine              proximity (within 3 positions)
                  retriev*                      prefix wildcard""");
        System.exit(1);
    }
}
