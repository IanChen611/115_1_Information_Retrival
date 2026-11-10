# IR HW2 — Query Processing and Searching 一頁說明文件

## 一、 組員與分工

| 姓名 | 學號 | 負責部分 | 主要職責與模組 | 分工比重 |
|:---|:---|:---|:---|:---|
|  |  | **工作一** 搜尋索引與 I/O | `SearchIndexBuilder`、`IndexReader`、`PostingList`、`SearchEngine`、`Main`（`index` / `search` 子命令）、Tokenizer 處理順序修正 | 25% |
|  |  | **工作二** 查詢解析與布林／位置查詢 | `QueryParser`、`QueryNode`、`QueryEvaluator`（AND / OR / NOT / 括號、片語、鄰近） | 25% |
|  |  | **工作三** 排序與權重 | `Weighting`、`Ranker`（TF-IDF 餘弦、lnc.ltc、BM25、top-k heap、champion list） | 25% |
|  |  | **工作四** 查詢擴充、測試與文件 | 前綴萬用字元、`SpellCorrector`、JUnit 測試、`results/` 輸出、說明文件 | 25% |

---

## 二、 主要系統特色與功能

1. **向量空間模型排序（作業指定公式）**：文件與查詢的詞權重皆為 `w = (1 + log10 tf) × log10(N / df)`，分數為兩向量的餘弦相似度；文件向量長度涵蓋該文件的所有詞，建索引時預先算好。
2. **三種必做查詢**：單一關鍵字（`Microsoft`）、自由文字（`United States government`）、簡單布林（`open source AND Linux`、`software engineer OR project manager`）。布林查詢先篩出符合的文件，再以餘弦相似度排序。
3. **查詢與索引使用同一個 Tokenizer**：大小寫摺疊、停用詞過濾、Porter stemming 的設定寫在 `meta.properties`，查詢時自動沿用，`Airplanes` 與 `airplane` 會對到同一個詞。
4. **高效率**：`lexicon.txt` 記錄每個詞在 `postings.txt`（234 MB）中的位元組位移，查詢時直接跳讀，不需掃描整個檔案。39,726 篇文件下，一般查詢 1–7 ms（程式啟動後第一筆約 30 ms）。
5. **加分功能**：
   - **完整布林運算**：`NOT`、括號、任意組合，優先序 `NOT > AND > OR`；AND 交集使用 HW1 的 `SkipList`。
   - **片語與鄰近查詢**：`"hong kong"`、`search /3 engine`，使用 HW1 索引的位置資訊。
   - **前綴萬用字元**：`retriev*`，重用 HW1 的 `Trie`。
   - **拼字校正**：查詢詞不在詞典時，以 Damerau-Levenshtein 編輯距離找最接近、df 最高的詞並提示（`microsfot` → `microsoft`）。
   - **多種權重**：`--weighting tfidf | lnc.ltc | bm25`。
   - **非精確 top-k**：`--champion` 只讀每個詞 tf 最高的 50 篇（champion list）；精確模式以 heap 取前 k 名。

---

## 三、 查詢語法

| 查詢 | 意義 |
|:---|:---|
| `hong kong business` | 自由文字：含任一詞的文件，依相似度排序 |
| `open source AND linux` | 布林運算元內的多個詞須全部出現；運算子 `AND` `OR` `NOT` 必須大寫 |
| `(linux OR windows) AND server AND NOT microsoft` | 括號與組合 |
| `"united states government"` | 片語（位置必須相鄰；被移除的停用詞仍佔位置） |
| `search /3 engine` | 鄰近：兩詞相距 3 個位置以內，不分先後 |
| `retriev*` | 前綴萬用字元（最多展開 df 最高的 500 個詞） |

---

## 四、 遇到的主要困難與解決方案

1. **HW1 索引缺少排序所需的資訊**
   - **困難**：HW1 只輸出文字格式的 `dictionary.txt`、`postings.txt`，沒有文件向量長度，也無法隨機存取；查一個詞要掃完整個 postings 檔。
   - **解決**：建索引後多掃一趟 `postings.txt`，產生 `lexicon.txt`（位移）、`norms.bin`（向量長度與文件長度）、`champions.bin`，HW1 的輸出格式完全不變。
2. **查詢與文件的正規化不一致**
   - **困難**：HW1 的索引未做大小寫摺疊，`hong kong` 查不到 `Hong Kong`；且 HW1 的 Tokenizer 先做 stemming 才檢查停用詞，`was` 會變成 `wa` 而漏網。
   - **解決**：HW2 固定以三個選項重建索引並記錄在 `meta.properties`；Tokenizer 改為先標記停用詞再 stemming，並補上測試。
3. **自由文字與布林查詢的語意**
   - **困難**：`open source AND Linux` 中的 `open source` 沒有運算子，要當成 OR 還是 AND 並不明確。
   - **解決**：整個查詢都沒有運算子時視為自由文字（任一詞命中、靠排序分高下）；作為布林運算元時則要求全部詞出現，需要嚴格相鄰時用雙引號片語。
4. **網頁語料本身含大量錯字**
   - **困難**：`microsft` 這類錯字本身就在詞典裡，單純取編輯距離最小的詞會把錯字校正成另一個錯字。
   - **解決**：改用把相鄰換位算一次編輯的 Damerau-Levenshtein 距離，距離相同時取 df 最高的詞。

---

## 五、 執行環境與安裝

- **JDK 17 以上**（已在 JDK 27 測試）。
- **Maven**：專案內附 Maven Wrapper（`mvnw` / `mvnw.cmd`），不需另外安裝。
- **外部函式庫**（由 Maven 自動下載，設定見 `pom.xml`）：`jsoup 1.23.2`（HTML 解析）、`lucene-analysis-common 9.12.3`（切詞與 Porter stemmer）、`junit-jupiter 5.14.4`（測試）。
- `ir.hw1` 套件是 HW1 的原始碼（WARC 解析、Tokenizer、位置索引、Trie、SkipList），HW2 新增的程式在 `ir.hw2`。

---

## 六、 編譯與執行指引

以下指令在 `HW2/` 目錄執行（Windows 用 `mvnw.cmd`，其他系統用 `./mvnw`）。

```bash
mvnw.cmd clean package
```

建立索引（約 26 秒，輸出到 `index/`，約 257 MB）：

```bash
java -Xmx4g -jar target/hw2.jar index --input ../HW1/data/10.warc.gz --output index
```

單次查詢：

```bash
java -jar target/hw2.jar search Hong Kong business
```

互動模式（出現 `cmd>` 後輸入查詢，`exit` 離開；片語與括號建議在此模式輸入）：

```bash
java -jar target/hw2.jar search
```

批次查詢並把結果寫入檔案：

```bash
java -jar target/hw2.jar search --queries results/queries.txt --output results/results.txt --verbose
```

| 選項 | 說明 |
|:---|:---|
| `--index-dir <dir>` | 索引目錄，預設 `index` |
| `--top-k <N>` | 輸出前 N 名，預設 10；0 表示全部 |
| `--weighting tfidf\|lnc.ltc\|bm25` | 權重方式，預設 `tfidf`（作業指定公式） |
| `--champion` | 只讀 champion list 的非精確 top-k |
| `--verbose` | 每列另附 TREC-ID 與 URL |
| `--output <file>` | 結果同時寫入檔案（UTF-8） |

執行測試：

```bash
mvnw.cmd test
```

---

## 七、 輸出格式

每列一筆結果，依相似度由高到低：`<docId> <TAB> <similarity score>`；加 `--verbose` 時為 `<docId> <TAB> <score> <TAB> <TREC-ID> <TAB> <URL>`。符合篇數、耗時與拼字校正提示寫到 stderr，不會混進結果。

```
cmd> Hong Kong business
18783	0.227822
4932	0.224856
18266	0.222311
```

- `results/queries.txt`：範例查詢（涵蓋投影片中的例子與各加分功能）。
- `results/results.txt`：上述查詢的排序結果。
- `docId` 對應 `index/docs.txt`（`docId <TAB> TREC-ID <TAB> URL`）。
