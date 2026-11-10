package ir.hw1.parser;

import org.jsoup.Jsoup;

import java.io.*;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.zip.GZIPInputStream;

/**
 * 依序讀取一個或多個 WARC 檔（.warc 或 .warc.gz），逐篇產出 {@link Document}。（負責：A）
 *
 * <ul>
 *   <li>只處理 WARC-Type 為 response 的 record，其餘略過</li>
 *   <li>trecId 取自 WARC-TREC-ID，url 取自 WARC-Target-URI</li>
 *   <li>去掉 HTTP header，HTML 用 jsoup 轉純文字（略過 script / style）</li>
 *   <li>docId 跨檔案連續編號，從 1 開始；本文為空的頁面略過且不佔編號</li>
 * </ul>
 *
 * <h2>WARC 檔是什麼</h2>
 * WARC（Web ARChive）是網路爬蟲用來「把抓到的網頁原封不動存起來」的格式。
 * 助教給的檔案名稱類似 {@code 00.warc.gz}：
 * <ul>
 *   <li>{@code .gz}：整個檔案用 gzip 壓縮過，要先解壓縮才看得到內容（Java 用 GZIPInputStream 邊讀邊解壓即可，不用先解壓到硬碟）</li>
 *   <li>解壓後是一個<b>純文字為主</b>的大檔案（約 1GB），裡面是<b>一筆接一筆的 record 串在一起</b>，約 4 萬筆</li>
 *   <li>每筆 record = 一段 WARC header ＋ 一段 content；一筆 response record 就是一個網頁</li>
 * </ul>
 *
 * <p>建議先親眼看一下檔案開頭（Git Bash）：{@code zcat 00.warc.gz | head -80}
 *
 * <h2>解壓後的檔案內容長這樣（ClueWeb09，WARC/0.18；換行為 \r\n，欄位順序可能略有不同）</h2>
 * <pre>
 * ┌─ 第 1 筆：warcinfo（整個檔案的說明，不是網頁 → 略過）──────────────
 * │ WARC/0.18                                         ← 每筆 record 都從這一行開始
 * │ WARC-Type: warcinfo
 * │ WARC-Date: 2009-03-65T08:43:19-0800
 * │ WARC-Record-ID: &lt;urn:uuid:993d3969-...&gt;
 * │ Content-Type: application/warc-fields
 * │ Content-Length: 219                               ← 空行之後 content 有幾個 byte
 * │                                                   ← 空行：WARC header 結束
 * │ software: Nutch 1.0-dev (modified for clueweb09)  ← content（剛好 219 bytes）
 * │ isPartOf: clueweb09-en
 * │ format: WARC file version 0.18
 * │ ...
 * │                                                   ← record 結尾的空行（通常 2 個）
 * ├─ 第 2 筆：response（一個網頁 → 要處理）──────────────────────────
 * │ WARC/0.18
 * │ WARC-Type: response                               ← 只處理 response
 * │ WARC-Target-URI: http://00000-nrt-realestate.homepagestartup.com/   → Document.url
 * │ WARC-Date: 2009-03-65T08:43:19-0800
 * │ WARC-Record-ID: &lt;urn:uuid:67f7cabd-...&gt;
 * │ WARC-TREC-ID: clueweb09-en0000-00-00000           → Document.trecId
 * │ Content-Type: application/http;msgtype=response
 * │ Content-Length: 16558                             ← 下面整段（HTTP header＋HTML）共 16558 bytes
 * │                                                   ← 空行：WARC header 結束，content 開始
 * │ HTTP/1.1 200 OK                                   ┐
 * │ Content-Type: text/html; charset=utf-8            │ HTTP header（伺服器回應的資訊）→ 丟掉，
 * │ Date: Tue, 13 Jan 2009 18:05:10 GMT               │ 但先從 Content-Type 取出 charset（編碼）
 * │ Content-Length: 16190                             ┘
 * │                                                   ← 空行：HTTP header 結束
 * │ &lt;html&gt;&lt;head&gt;&lt;title&gt;NRT Real Estate&lt;/title&gt;...      ┐ HTML 本文 → 用 jsoup 轉成純文字
 * │ ...&lt;/body&gt;&lt;/html&gt;                                 ┘ → Document.text
 * │                                                   ← record 結尾的空行
 * ├─ 第 3 筆：response ...
 * │ WARC/0.18
 * │ ...
 * └─ ...（約 4 萬筆，直到檔案結尾）
 * </pre>
 *
 * <p>讀的方法：逐行讀 WARC header 直到空行 → 依 Content-Length 一次讀固定 byte 數的 content
 * → 跳過結尾空行 → 下一筆。<b>不能用「找下一個 WARC/0.18」來切 record</b>，因為網頁內容裡也可能出現這個字串。
 *
 * <p>注意：ClueWeb09 是舊版 WARC/0.18，jwarc 不一定能讀。建議先用 jwarc 試讀一個檔案，
 * 若會報錯，就照上面的格式自己解析（用 {@code BufferedInputStream} 逐行讀 header，
 * 再依 Content-Length 讀固定位元組數），並把 pom.xml 中的 jwarc 相依移除。
 *
 * <h2>建議的內部設計</h2>
 * 採「預讀一篇」的方式：用欄位 {@code Document nextDoc} 暫存下一篇。
 * {@code hasNext()} 若 nextDoc 為 null 就嘗試讀下一篇；{@code next()} 回傳 nextDoc 並清空。
 * 這樣 hasNext() 才能正確處理「後面全是要略過的 record」或「目前檔案讀完要換下一個檔案」的情況。
 */
public class WarcReader implements DocumentSource {

    private static final int BUFFER_SIZE = 1 << 16;  // 64KB

    private final Iterator<Path> fileIterator;  // 還沒開的檔案
    private InputStream current;                // 目前正在讀的檔案串流（讀取游標）；全部讀完後為 null
    private int nextDocId = 1;                  // 下一篇文件要給的 docId，跨檔案持續遞增
    private Document nextDoc;                   // 預讀的下一篇（見類別說明「建議的內部設計」）

    /**
     * @param files 要依序讀取的 WARC 檔路徑，至少一個（例如 [data/00.warc.gz, data/01.warc.gz]）
     */
    public WarcReader(List<Path> files) throws IOException {
        // 建構子只做準備工作，不讀檔案內容：記住檔案清單、打開第一個檔案成為串流。
        // 真正的讀取在 hasNext() 被呼叫時才一次讀一筆 record。
        if (files == null || files.isEmpty()) {
            throw new IllegalArgumentException("no input files");
        }
        this.fileIterator = List.copyOf(files).iterator();
        openNextFile();
    }

    /**
     * 關閉目前的檔案並開啟下一個。
     *
     * @return 成功開啟回傳 true；所有檔案都已讀完回傳 false（此時 current 為 null）
     */
    private boolean openNextFile() throws IOException {
        if (current != null) {
            current.close();
            current = null;
        }
        if (!fileIterator.hasNext()) {
            return false;
        }
        Path path = fileIterator.next();
        InputStream raw = new BufferedInputStream(Files.newInputStream(path), BUFFER_SIZE);
        if (path.getFileName().toString().endsWith(".gz")) {
            try {
                // 邊讀邊解壓，不用先解壓到硬碟；外層再包一次 Buffered，逐 byte 讀 header 才不會慢
                current = new BufferedInputStream(new GZIPInputStream(raw, BUFFER_SIZE), BUFFER_SIZE);
            } catch (IOException e) {
                raw.close();  // 不是合法的 gzip 檔：關掉已開的檔案再往外丟
                throw new IOException("not a valid gzip file: " + path, e);
            }
        } else {
            current = raw;
        }
        return true;
    }

    @Override
    public boolean hasNext() {
        if (nextDoc == null) {
            try {
                nextDoc = readNextDocument();
            } catch (IOException e) {
                throw new UncheckedIOException(e);   // hasNext() 不能丟 IOException，所以要包起來
            }
        }
        return nextDoc != null;
    }

    @Override
    public Document next() {
        if (!hasNext()) throw new NoSuchElementException("has no next document");
        Document doc = nextDoc;   // 先拿出來
        nextDoc = null;           // 清掉，表示「這篇已經交出去了」
        return doc;
    }

    @Override
    public void close() throws IOException {
        // Main 用 try-with-resources，程式結束或出錯時會自動呼叫這裡
        if (current != null) {
            current.close();
            current = null;
        }
    }

    //  readNextDocument() 的流程（迴圈直到讀到一篇有效文件或所有檔案都讀完）：
    //   a. 從目前檔案讀下一筆 record 的 WARC header（逐行讀到空行為止），解析成 Map<String,String>
    //      - 讀到檔案結尾（EOF）→ 關閉目前檔案、開下一個檔案；沒有下一個檔案了就回傳 null
    //   b. 依 Content-Length 讀出 content 的 byte[]（要用位元組讀，不能用字元，否則中文等多位元組字會讀錯長度）
    //   c. WARC-Type 不是 "response" → 略過，continue
    //   d. 把 content 切成 HTTP header 與 HTML body（找第一個 "\r\n\r\n"，找不到再試 "\n\n"）
    //   e. 決定編碼：從 HTTP header 的 "Content-Type: ...; charset=xxx" 取 charset，
    //      沒有或不認得（Charset.isSupported 為 false）就用 UTF-8
    //   f. 用 jsoup 轉純文字：Jsoup.parse(html).text()
    //      （jsoup 的 text() 本來就不含 <script>、<style> 的內容）
    //   g. 純文字 isBlank() → 略過，continue（不佔 docId）
    //   h. 回傳 new Document(nextDocId++, header.get("WARC-TREC-ID"), header.get("WARC-Target-URI"), text)
    //
    //  ※ 某些網頁 HTML 可能壞掉讓 jsoup 出錯：用 try-catch 包住單篇，出錯就印警告並略過，不要讓整個程式中斷
    private Document readNextDocument() throws IOException {
        while (current != null) {                          // current == null 代表所有檔案都讀完了
            Map<String, String> header = readWarcHeader(); // a. 讀 WARC header
            if (header == null) {                          //    null = 這個檔案讀到結尾了
                openNextFile();                            //    換下一個檔案（沒有了會把 current 設成 null，迴圈就結束）
                continue;
            }

            String contentLength = header.get("Content-Length");
            if (contentLength == null) {
                throw new IOException("WARC record without Content-Length: " + header);
            }
            int length = Integer.parseInt(contentLength);
            byte[] content = current.readNBytes(length);   // b. 一次讀固定 byte 數，游標剛好停在這筆 record 結尾

            if (!"response".equals(header.get("WARC-Type"))) {
                continue;                                  // c. 不是網頁（例如 warcinfo）→ 讀下一筆
            }

            // d. 切開 HTTP header 與 HTML body：找 content 中的第一個空行
            //    用 ISO_8859_1 轉字串：它 1 個 byte 剛好對應 1 個字元，所以字串中的位置 = byte 陣列中的位置
            String raw = new String(content, StandardCharsets.ISO_8859_1);
            int headerEnd = raw.indexOf("\r\n\r\n");
            int bodyStart = headerEnd + 4;
            if (headerEnd < 0) {
                headerEnd = raw.indexOf("\n\n");
                bodyStart = headerEnd + 2;
            }
            if (headerEnd < 0) {
                continue;                                  // 沒有空行 → 沒有 HTML 本文，略過
            }
            String httpHeader = raw.substring(0, headerEnd);

            // e. 決定編碼：HTTP header 有寫 charset 就用；沒有則傳 null 給 jsoup，
            //    jsoup 會自己找 HTML 裡的 <meta charset=...>，都沒有就用 UTF-8
            String charsetName = extractCharset(httpHeader);

            // f. 用 jsoup 把 HTML 轉成純文字（text() 不含 <script>、<style> 的內容）
            String trecId = header.getOrDefault("WARC-TREC-ID", "");
            String text;
            try (InputStream html = new ByteArrayInputStream(content, bodyStart, content.length - bodyStart)) {
                text = Jsoup.parse(html, charsetName, "").text();
            } catch (IOException | RuntimeException e) {
                // 壞掉的網頁只略過這一篇，不讓整個程式中斷
                System.err.println("Warning: skip " + trecId + " (" + e + ")");
                continue;
            }

            // g. 沒有任何文字（例如純圖片頁）→ 略過，不佔 docId
            if (text.isBlank()) {
                continue;
            }

            // h. 建立文件；docId 只在這裡遞增，所以被略過的 record 不會佔編號
            return new Document(nextDocId++, trecId, header.get("WARC-Target-URI"), text);
        }
        return null;
    }

    /**
     * 從 HTTP header 中取出 charset，例如 "Content-Type: text/html; charset=UTF-8" → "UTF-8"。
     *
     * @return Java 支援的編碼名稱；沒寫或不認得則回傳 null
     */
    private static String extractCharset(String httpHeader) {
        int idx = httpHeader.toLowerCase(Locale.ROOT).indexOf("charset=");
        if (idx < 0) {
            return null;
        }
        int start = idx + "charset=".length();
        int end = start;
        while (end < httpHeader.length() && "\r\n;, ".indexOf(httpHeader.charAt(end)) < 0) {
            end++;                                         // 讀到 ; 空白 換行 為止
        }
        String name = httpHeader.substring(start, end).replace("\"", "").replace("'", "");
        try {
            return Charset.isSupported(name) ? name : null;
        } catch (IllegalArgumentException e) {             // 名稱含非法字元（網頁亂寫的）
            return null;
        }
    }

    /** 從 current 讀一行（讀到 \n 為止），去掉結尾的 \r；檔案結尾回傳 null。 */
    private String readLine() throws IOException {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        int b;
        while ((b = current.read()) != -1 && b != '\n') {   // read() 一次讀 1 個 byte，-1 代表檔案結尾
            buf.write(b);
        }
        if (b == -1 && buf.size() == 0) return null;        // 什麼都沒讀到就到結尾了 → EOF
        String line = buf.toString(StandardCharsets.UTF_8);
        return line.endsWith("\r") ? line.substring(0, line.length() - 1) : line;
    }

    /** 讀一筆 record 的 WARC header，存成 Map（例如 "WARC-Type" → "response"）；檔案結尾回傳 null。 */
    private Map<String, String> readWarcHeader() throws IOException {
        String line;
        do {                                   // 跳過上一筆 record 結尾的空行
            line = readLine();
            if (line == null) return null;     // 檔案結尾
        } while (line.isEmpty());

        if (!line.startsWith("WARC/")) {
            throw new IOException("expected 'WARC/' but got: " + line);
        }
        Map<String, String> header = new HashMap<>();
        while ((line = readLine()) != null && !line.isEmpty()) {   // 讀到空行 = header 結束
            int colon = line.indexOf(':');                          // "WARC-Type: response" 用第一個冒號切開
            if (colon > 0) {
                header.put(line.substring(0, colon).trim(), line.substring(colon + 1).trim());
            }
        }
        return header;
    }
}
