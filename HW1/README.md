# IR HW1 — Document Indexing 一頁說明文件

## 一、 組員與分工

| 姓名 | 學號 | 負責部分 | 主要職責與模組 | 分工比重 |
|:---|:---|:---|:---|:---|
| 陳翊揚 | 112820025 | **組員 A** | WARC 解析、主程式命令列串接、Skip pointers、`docs.txt` 產出 | 25% |
| 辛政隆 | 112820003 | **組員 B** | Tokenizer 斷詞器、Case folding、Stemming、Trie 前綴索引 | 25% |
| 杜詠霖 | 112820042 | **組員 C** | Positional Inverted Index 建構、分批區塊儲存與多路合併 (SPIMI)、輸出格式化 | 25% |
| 陳昱霖 | 112820030 | **組員 D** | 停用詞模組 (`StopwordList`)、測試驗證集建置、一頁說明文件撰寫、進度管理與打包 | 25% |

---

## 二、 主要系統特色與功能

1. **支援巨量網頁語料串流解析（WARC / WARC.GZ）**：
   - 直接讀取壓縮之 WARC 檔案（ClueWeb09 樣本，約 40,000 篇網頁、1GB 語料）。
   - 過濾非 `response` 紀錄、去除 HTTP Headers，並以 Jsoup 萃取乾淨 HTML body 本文，自動過濾 `<script>` 與 `<style>` 標籤。
   - 輸出文件對照表 `docs.txt`（紀錄內部 `docId`、`TREC-ID` 與原始 `URL`）。
2. **標準位置倒排索引（Positional Inverted Index）**：
   - 依據教科書標準格式，為每個詞彙紀錄 Document Frequency (`df`)、各文件的 Term Frequency (`tf`) 以及出現之精準字詞位置 (`pos`)。
   - 支援後續片語查詢（Phrase Query）與鄰近搜尋（Proximity Search）。
3. **外部記憶體分批索引與多路合併（SPIMI / BSBI 架構）**：
   - 為避免 1GB 語料在單機建置時發生記憶體不足（Out-Of-Memory, OOM），設計以 128 MiB 為門檻之二進位中介區塊（`IndexBlock`）。
   - 透過優先佇列（PriorityQueue）執行外排序多路合併（`IndexMerger`），平滑串流輸出最終字典與倒排清單。
4. **靈活的語言前處理選項開關（Parameter Triggers）**：
   - 支援 `--casefold`（大小寫正規化）、`--stopword`（常用英文停用詞過濾）、`--stem`（Porter 詞幹提取），可個別或組合切換。
5. **加分進階功能（Bonus Functionality）**：
   - **Trie 字典樹前綴檢索**：建立詞彙 Trie，支援 `--prefix` 命令即時檢索任意前綴匹配的所有詞彙與 Postings。
   - **Skip Pointers（跳躍指標）**：實作 `SkipList` 資料結構，支援日後加速多詞交集查詢。

---

## 三、 Tokenizer 斷詞規則

斷詞由組員 B 實作、組員 D 整合驗證，採用基於 Lucene 的標準斷詞處理流程：
1. **處理流程順序**：
   > `原始文字` ➔ `切詞 (Tokenize)` ➔ `大小寫轉換 (Case Folding)` ➔ `停用詞過濾 (Stopword Removal)` ➔ `詞幹提取 (Stemming)`
2. **斷詞與標點規則**：
   - **空白與標點分隔**：依空格、換行及常見標點符號（如逗號、句號、驚嘆號等）斷詞。
   - **保留詞內特殊標點**：智慧保留特定語意符號，例如單字縮寫撇號（`it's`、`don't`）或連字號。
   - **英數混合連貫性**：字母與數字組合（如 `Lucene6.0`、`MP3`）視為單一 Token，不予切斷。
   - **電子郵件與網址**：自動辨識並完整保留 Email（如 `user@example.com`）與 URL（如 `https://example.org`）。
   - **CJK（中日韓）文字支援**：對於非空格分隔之東亞語系文字，自動採單字切分（One-Gram）。
3. **位置編號（Position）保留原則**：
   - 即使單字被停用詞表過濾，後續單字的位置編號仍保持原出現順序（例如 `go to bed` 在過濾 `to` 後，`go` 為 1、`bed` 為 3），以確保片語與鄰近比對的正確性。

---

## 四、 遇到的主要困難與解決方案

在開發與整合整個檢索系統的過程中，小組面臨並克服了以下四大核心挑戰：

1. **巨量語料（1GB / 4 萬篇網頁）引發的記憶體限制與外部排序（SPIMI）**：
   - **困難**：助教提供的 ClueWeb09 樣本檔案約 1GB、包含近 40,000 篇網頁。若將所有網頁的 Token 與倒排清單（Postings）全數累積在記憶體中一次性排序輸出，會立即超出 Java Heap 限制並引發 `OutOfMemoryError`（記憶體溢位）。
   - **解決**：團隊實作了類似 **SPIMI（Single-pass In-memory Indexing）** 的外部記憶體演算法。程式設定 128 MiB 的記憶體軟門檻，當累積索引達到上限時，即在文件邊界將排序好的二進位區塊（`IndexBlock`）寫出至硬碟暫存；最後再利用**優先佇列（PriorityQueue）**進行外排序多路合併（`IndexMerger`），平滑串流輸出最終字典與倒排檔，成功在 31.6 秒內穩定處理完全部網頁且完全不爆記憶體。

2. **異質網路語料的格式清洗與編碼容錯（WARC / HTML Parsing）**：
   - **困難**：真實網路爬取的 WARC 檔案結構複雜，夾雜了各類 HTTP 標頭、非 `response` 紀錄、畸形 HTML 語法，以及 `<script>`、`<style>` 與多國語系編碼（UTF-8、ISO-8859-1 等）雜訊，若直接斷詞會產生大量無意義字串甚至使剖析中斷。
   - **解決**：在剖析串流中加入嚴格的 WARC 標頭狀態檢查，精確鎖定 `response` 區塊；並整合 Jsoup 進行容錯 DOM 樹建置，自動過濾所有 script 與 style 節點，僅萃取純淨 HTML body 文字；同時採用串流 GZIP 解壓技術，省去將 1GB 檔案解壓至硬碟的額外空間與時間開銷。

3. **斷詞管線順序與字詞位置（Position）編號保留**：
   - **困難**：在支援大小寫轉換、停用詞過濾與詞幹提取（Porter Stemming）的多種參數組合下，若先過濾停用詞再計算 Token 位置，會導致詞彙間隔資訊遺失，使後續的片語查詢（Phrase Query）與鄰近搜尋無法正確判定相鄰距離；且若先進行詞幹提取再過濾停用詞，會因字根變化而漏過濾停用詞。
   - **解決**：團隊制定了嚴謹的語言前處理管線順序：`切詞` ➔ `Case Folding` ➔ `Stopword Filtering` ➔ `Stemming`。在切詞當下即綁定全局累計的位置序號，即使該詞被停用詞表剔除，後續詞項依然保留原始空缺的位置編號（例如 `go to bed` 移除 `to` 後保留 `go:1`, `bed:3`），完美兼顧語言過濾與位置精確度。

4. **多參數組合下的索引正確性驗證與品質把關**：
   - **困難**：正式資料產出的詞項高達 73 萬個、倒排清單超過 300 MB，單靠人工肉眼抽查極難驗證 df、tf、位置編號是否絕對嚴格遞增，也難以第一時間抓出多模組整合時的邊界條件錯誤。
   - **解決**：小組自製微型代表性語料庫（4 篇測試文本），嚴格手算出多種參數組合下的標準答案（Ground Truth）；並開發自動化審計比對腳本（`verify.py` 與 `audit.py`），對輸出成果的語法格式、字典序遞增、數值一致性進行全面驗證，確保上傳與 Demo 前所有規格 100% 吻合教授要求。

---

## 五、 執行環境與安裝

- **Java 版本**：JDK 17 以上
- **建置工具**：Apache Maven 3.9 以上
- **第三方相依套件**（透過 Maven 自動下載管理）：
  - `jsoup` (HTML 解析與本文清洗)
  - `lucene-analysis-common` (Lucene StandardTokenizer 與 PorterStemmer)
  - `junit-jupiter` (單元測試，JUnit 5)

---

## 六、 編譯與執行指引

### 1. 編譯專案
在專案根目錄（`HW1/`）下執行：
```bash
mvn clean package
```
編譯完成後將於 `target/` 目錄下產生可執行檔 `hw1.jar`。

### 2. 執行建置倒排索引
```bash
java -Xmx4g -jar target/hw1.jar --input data/xxx.warc.gz --output out/ [--casefold] [--stopword] [--stem]
```
*參數說明*：
- `--input`：輸入的 WARC 資料集路徑（可指定多次）。
- `--output`：輸出結果放置目錄（預設為 `out/`）。
- `--casefold`：（選用）啟用大小寫正規化（轉為小寫）。
- `--stopword`：（選用）啟用停用詞過濾。
- `--stem`：（選用）啟用 Porter 詞幹提取。

### 3. 執行前綴查詢（Trie Prefix Query）
```bash
java -jar target/hw1.jar --index-dir out/ --prefix retriev
```
程式會讀取 `out/dictionary.txt` 建置 Trie 字典樹，並依字典序輸出 `out/postings.txt` 中符合前綴的所有詞項與其倒排清單。

---

## 七、 輸出檔案格式

執行完畢後，輸出目錄（`out/`）將產生以下三個檔案：

1. **`dictionary.txt`**：
   - 格式：`term<TAB>df`（按 term 的 Java `String.compareTo` 字典序排列）
   ```
   information	3
   retrieval	4
   search	3
   ```
2. **`postings.txt`**：
   - 格式：`term, df: <docId, tf: <pos1, pos2, ...>; ...>`
   ```
   retrieval, 4: <1, 1: <2>; 2, 1: <1>; 3, 1: <3>; 4, 1: <4>>
   ```
3. **`docs.txt`**：
   - 格式：`docId<TAB>TREC-ID<TAB>URL`
   ```
   1	clueweb09-en0000-00-00000	http://example.com/page1
   ```
