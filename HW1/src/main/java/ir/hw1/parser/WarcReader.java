package ir.hw1.parser;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

/**
 * 依序讀取一個或多個 WARC 檔（.warc 或 .warc.gz），逐篇產出 {@link Document}。（負責：A）
 *
 * <ul>
 *   <li>只處理 WARC-Type 為 response 的 record，其餘略過</li>
 *   <li>trecId 取自 WARC-TREC-ID，url 取自 WARC-Target-URI</li>
 *   <li>去掉 HTTP header，HTML 用 jsoup 轉純文字（略過 script / style）</li>
 *   <li>docId 跨檔案連續編號，從 1 開始；本文為空的頁面略過且不佔編號</li>
 * </ul>
 */
public class WarcReader implements DocumentSource {

    public WarcReader(List<Path> files) throws IOException {
        // TODO(A)
    }

    @Override
    public boolean hasNext() {
        // TODO(A)
        throw new UnsupportedOperationException("TODO(A): WarcReader.hasNext");
    }

    @Override
    public Document next() {
        // TODO(A)
        throw new UnsupportedOperationException("TODO(A): WarcReader.next");
    }

    @Override
    public void close() throws IOException {
        // TODO(A)
    }
}
