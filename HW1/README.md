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
   - 依據教科書標準格式，為每個詞彙紀錄 Document Frequency ($df$)、各文件的 Term Frequency ($tf$) 以及出現之精準字詞位置 ($pos$)。
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
   $$\text{原始文字} \longrightarrow \text{切詞 (Tokenize)} \longrightarrow \text{Case Folding} \longrightarrow \text{Stopword Removal} \longrightarrow \text{Stemming}$$
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

1. **WARC 異質網頁清洗與編碼混亂（組員 A）**：
   - **困難**：
   - **解決**：
2. **前處理順序與位置編號漂移（組員 B）**：
   - **困難**：
   - **解決**：
3. **1GB 語料記憶體暴增與排序瓶頸（組員 C）**：
   - **困難**：
   - **解決**：
4. **停用詞表相容性與多條件測試比對（組員 D）**：
   - **困難**：大小寫尚未正規化前可能導致大寫停用詞（如 `The`、`To`）未被識別；多種參數組合下不易人工驗證正確性。
   - **解決**：`StopwordList` 採用大小寫無關（Case-insensitive）的比對；並自製 4 篇代表性小測試文件，精準手算出不同參數下的 Ground Truth，大幅降低除錯成本。

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
