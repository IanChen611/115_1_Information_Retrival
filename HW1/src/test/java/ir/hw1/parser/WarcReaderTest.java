package ir.hw1.parser;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;

import static org.junit.jupiter.api.Assertions.*;

/**
 * WarcReader 的單元測試。（負責：A）執行：mvn test，或在 IntelliJ 點類別旁的綠色箭頭。
 *
 * <h2>測試資料（src/test/resources/warc/）</h2>
 * <b>sample.warc.gz</b>（sample.warc 是同內容的未壓縮版，可用文字編輯器打開看）依序有 5 筆 record：
 * <table border="1">
 *   <tr><th>#</th><th>WARC-Type</th><th>WARC-TREC-ID / URI</th><th>內容</th><th>預期</th></tr>
 *   <tr><td>1</td><td>warcinfo</td><td>—</td><td>檔案說明</td><td>略過</td></tr>
 *   <tr><td>2</td><td>response</td><td>clueweb09-en0000-00-00000<br>http://example.com/hello</td>
 *       <td>HTTP header 有 charset=utf-8；HTML 含 &lt;title&gt;Hello Page、&lt;h1&gt;Hello World、
 *       中文「資訊檢索」，以及 &lt;script&gt; 內的 SCRIPT_TEXT、&lt;style&gt; 內的 color:red</td>
 *       <td><b>docId 1</b>；text 含 "Hello World"、"資訊檢索"；<b>不含</b> "SCRIPT_TEXT"、"color:red"</td></tr>
 *   <tr><td>3</td><td>request</td><td>—</td><td>HTTP 請求</td><td>略過</td></tr>
 *   <tr><td>4</td><td>response</td><td>clueweb09-en0000-00-00001</td><td>只有一張圖片，沒有文字</td>
 *       <td>略過（且不佔 docId）</td></tr>
 *   <tr><td>5</td><td>response</td><td>clueweb09-en0000-00-00002<br>http://example.com/cafe</td>
 *       <td>HTTP header <b>沒有</b> charset 且只用 \n 換行；編碼寫在 &lt;meta&gt; 為 ISO-8859-1，
 *       內文 "café menu"（é 是單一 byte 0xE9）；內文中還故意放了一段 "WARC/0.18" 字樣</td>
 *       <td><b>docId 2</b>；text 含 "café"（編碼判斷正確）、含 "WARC/0.18"（沒有被誤判為新 record）</td></tr>
 * </table>
 *
 * <b>sample2.warc</b>（未壓縮）：warcinfo ＋ 1 筆 response
 * （clueweb09-en0000-01-00000，http://example.org/second，內文 "Second file page"）。
 *
 * <p>※ 這些檔案的 Content-Length 是精確計算的，不要用編輯器修改後存檔（換行可能被改掉）。
 * 需要改測試資料請重新產生。
 */
class WarcReaderTest {

    /** 取得 src/test/resources 底下測試檔的路徑，例如 resource("warc/sample.warc.gz")。 */
    private static Path resource(String name) {
        try {
            return Path.of(WarcReaderTest.class.getResource("/" + name).toURI());
        } catch (URISyntaxException | NullPointerException e) {
            throw new IllegalStateException("test resource not found: " + name, e);
        }
    }

    /** 用 WarcReader 依序讀完這些檔案，回傳所有文件。 */
    private static List<Document> readAll(String... names) throws IOException {
        List<Path> paths = new ArrayList<>();
        for (String name : names) {
            paths.add(resource("warc/" + name));
        }
        List<Document> docs = new ArrayList<>();
        try (WarcReader reader = new WarcReader(paths)) {
            while (reader.hasNext()) {
                docs.add(reader.next());
            }
        }
        return docs;
    }

    @Test
    void readsOnlyValidResponseRecords() throws Exception {
        // warcinfo、request、空白頁都要被略過
        List<Document> docs = readAll("sample.warc.gz");
        assertEquals(2, docs.size());
    }

    @Test
    void assignsConsecutiveDocIdsStartingFromOne() throws Exception {
        List<Document> docs = readAll("sample.warc.gz");

        assertEquals(1, docs.get(0).docId());
        assertEquals("clueweb09-en0000-00-00000", docs.get(0).trecId());
        // 中間的空白頁（00001）被略過，不佔 docId
        assertEquals(2, docs.get(1).docId());
        assertEquals("clueweb09-en0000-00-00002", docs.get(1).trecId());
    }

    @Test
    void extractsUrl() throws Exception {
        List<Document> docs = readAll("sample.warc.gz");
        assertEquals("http://example.com/hello", docs.get(0).url());
        assertEquals("http://example.com/cafe", docs.get(1).url());
    }

    @Test
    void stripsHtmlScriptAndStyle() throws Exception {
        String text = readAll("sample.warc.gz").get(0).text();

        assertTrue(text.contains("Hello World"), text);
        assertTrue(text.contains("資訊檢索"), "UTF-8 中文應正確解碼：" + text);
        assertFalse(text.contains("SCRIPT_TEXT"), "<script> 內容不應出現：" + text);
        assertFalse(text.contains("color:red"), "<style> 內容不應出現：" + text);
        assertFalse(text.contains("<h1>"), "HTML 標籤應被去除：" + text);
        assertFalse(text.contains("HTTP/1.1"), "HTTP header 應被去除：" + text);
    }

    @Test
    void detectsCharsetFromMetaTag() throws Exception {
        // HTTP header 沒寫 charset，只有 <meta> 寫 ISO-8859-1；é 在檔案中是單一 byte 0xE9
        String text = readAll("sample.warc.gz").get(1).text();
        assertTrue(text.contains("café"), "編碼判斷錯誤，得到：" + text);
    }

    @Test
    void usesContentLengthNotWarcMarkerToSplitRecords() throws Exception {
        List<Document> docs = readAll("sample.warc.gz");

        // 內文中的 "WARC/0.18" 屬於第 2 篇的內容，不是新的 record
        assertTrue(docs.get(1).text().contains("WARC/0.18"), docs.get(1).text());
        assertEquals(2, docs.size());
    }

    @Test
    void readsUncompressedFile() throws Exception {
        List<Document> plain = readAll("sample.warc");
        List<Document> gzipped = readAll("sample.warc.gz");
        assertEquals(gzipped, plain);
    }

    @Test
    void continuesDocIdsAcrossFiles() throws Exception {
        List<Document> docs = readAll("sample.warc.gz", "sample2.warc");

        assertEquals(3, docs.size());
        Document third = docs.get(2);
        assertEquals(3, third.docId());
        assertEquals("clueweb09-en0000-01-00000", third.trecId());
        assertEquals("http://example.org/second", third.url());
        assertTrue(third.text().contains("Second file page"), third.text());
    }

    @Test
    void nextThrowsWhenExhausted() throws Exception {
        try (WarcReader reader = new WarcReader(List.of(resource("warc/sample2.warc")))) {
            assertTrue(reader.hasNext());
            reader.next();
            assertFalse(reader.hasNext());
            assertFalse(reader.hasNext(), "讀完後重複呼叫 hasNext() 也應為 false");
            assertThrows(NoSuchElementException.class, reader::next);
        }
    }

    @Test
    void hasNextDoesNotSkipDocuments() throws Exception {
        try (WarcReader reader = new WarcReader(List.of(resource("warc/sample.warc.gz")))) {
            assertTrue(reader.hasNext());
            assertTrue(reader.hasNext());
            assertTrue(reader.hasNext());
            assertEquals(1, reader.next().docId());
            assertEquals(2, reader.next().docId(), "不先呼叫 hasNext() 直接 next() 也要能用");
        }
    }

    @Test
    void rejectsEmptyFileList() {
        assertThrows(IllegalArgumentException.class, () -> new WarcReader(List.of()));
    }
}
