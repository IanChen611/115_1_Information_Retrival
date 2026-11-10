package ir.hw2;

import ir.hw1.index.Indexer;
import ir.hw1.index.PositionalIndexer;
import ir.hw1.text.DefaultTokenizer;
import ir.hw1.text.StopwordList;
import ir.hw1.text.Tokenizer;
import ir.hw1.text.TokenizerOptions;
import ir.hw2.index.SearchIndexBuilder;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** 測試用：把幾段文字直接建成完整的搜尋索引（docId 從 1 起算）。（工作四） */
final class TestIndex {

    private TestIndex() {
    }

    static void build(Path dir, int championSize, String... texts) throws IOException {
        TokenizerOptions options = TokenizerOptions.all();
        Tokenizer tokenizer = new DefaultTokenizer(options, StopwordList.loadDefault());
        Indexer indexer = new PositionalIndexer(dir.resolve("blocks-tmp"), 1024 * 1024);
        try (BufferedWriter docs = Files.newBufferedWriter(dir.resolve("docs.txt"), StandardCharsets.UTF_8)) {
            for (int i = 0; i < texts.length; i++) {
                int docId = i + 1;
                docs.write(docId + "\tdoc-" + docId + "\thttp://example.test/" + docId + "\n");
                indexer.addDocument(docId, tokenizer.tokenize(texts[i]));
            }
        }
        indexer.finish(dir);
        SearchIndexBuilder.build(dir, options, championSize);
    }
}
