package ir.hw1.parser;

import java.io.Closeable;
import java.util.Iterator;

/**
 * 逐篇提供文件的來源（A 實作，例如 WarcReader）。
 *
 * <p>一次只回傳一篇，不要一次把整個 1GB 檔案讀進記憶體。
 * 非 response 類型的 WARC record（warcinfo、request 等）與本文為空的頁面應直接略過，
 * 不佔用 docId。
 *
 * <pre>{@code
 * try (DocumentSource src = new WarcReader(path)) {
 *     while (src.hasNext()) {
 *         Document doc = src.next();
 *         ...
 *     }
 * }
 * }</pre>
 */
public interface DocumentSource extends Iterator<Document>, Closeable {
}
