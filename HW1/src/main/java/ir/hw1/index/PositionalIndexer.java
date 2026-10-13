package ir.hw1.index;

import ir.hw1.text.Token;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

/**
 * {@link Indexer} 的實作。（負責：C）
 *
 * <p>資料量大時記憶體可能不夠：可在記憶體中累積一批後寫出暫存檔（block），
 * 最後在 {@link #finish(Path)} 中 k-way merge 成最終的 dictionary.txt 與 postings.txt（SPIMI / BSBI）。
 */
public class PositionalIndexer implements Indexer {

    @Override
    public void addDocument(int docId, List<Token> tokens) throws IOException {
        // TODO(C)
        throw new UnsupportedOperationException("TODO(C): PositionalIndexer.addDocument");
    }

    @Override
    public void finish(Path outputDir) throws IOException {
        // TODO(C)
        throw new UnsupportedOperationException("TODO(C): PositionalIndexer.finish");
    }
}
