# C 模組：Positional inverted index

## 與 A、B 的介面

`Main` 可以繼續使用 `new PositionalIndexer()`，不需要修改呼叫方式。

- `addDocument(docId, tokens)`：docId 為正整數且嚴格遞增，可有間隔；tokens 的 position 嚴格遞增，空列表合法。
- term 與 position 直接使用 Token 的值。C 不做大小寫、停用詞或 stemming 處理，也不重新編號位置。
- 每個 term／doc 只有一筆 posting；tf 為位置數，df 為包含該 term 的文件數。
- `finish(outputDir)` 寫出 UTF-8 `dictionary.txt` 與 `postings.txt`，term 依 Java `String.compareTo` 排序，docId 與 positions 遞增。
- finish 成功後不能再次 finish 或加入文件。I/O 失敗後也不能重用；參數驗證失敗則不會加入部分文件或消耗 docId。

## 實作方式

1. 每篇文件依 term 分組，用 primitive int buffer 累積 positions。
2. 累積記憶體區塊；超過估計門檻時，在完整文件的邊界寫出排序過的二進位區塊。
3. 區塊中每個 term 先記錄 df，再記錄各篇的 docId、tf、positions。
4. 合併時以 priority queue 選出相同 term，先加總 df，再逐筆輸出 postings，避免將熱門詞的整條 postings 載入記憶體。
5. 區塊的文件範圍不重疊，按建立順序串接即可維持 docId 順序。多輪合併只合併相鄰區塊，保留此條件。

`PositionalIndexer` 負責累積與生命週期，`IndexBlock` 負責中間格式，`IndexMerger` 負責合併與最終格式。

## 記憶體與中間檔

無參數版本的預設值：

- 估計記憶體軟門檻：128 MiB。
- 每輪最多同時讀取 32 個區塊。
- 工作目錄：`target/index-blocks/run-*`，第一次寫出區塊時才建立。

可在 Java 中調整，不需要改動 `Indexer` 介面：

```java
Indexer indexer = new PositionalIndexer(
        Path.of("target", "custom-index-blocks"),
        64L * 1024 * 1024,
        16);
```

估計值包含詞彙、posting 與位置數，並非精確的 JVM heap 用量。現有 Tokenizer 會先產出整篇 `List<Token>`，因此記憶體峰值仍取決於最大單篇文件；索引器不截斷文件以符合門檻。

所有原始區塊與合併區塊都保留，不使用刪除、啟動時清理或 `deleteOnExit`。每個 indexer 使用獨立的工作子目錄。保留檔案會占用磁碟空間，使用者可另行決定保存期限。

I/O 錯誤會帶出保留中間檔的位置；失敗可能留下不完整區塊或最終輸出，應以本次執行成功完成為索引有效的依據。最終文字檔沿用專案行為，會寫入或覆寫指定目錄的同名檔案。

## 驗證

一般環境使用 `mvn test`。本機目前缺少 pom 指定的 Surefire 3.5.6 部分相依，可在不修改 pom 的情況下，使用已快取版本離線驗證：

```powershell
mvn -o test-compile org.apache.maven.plugins:maven-surefire-plugin:3.1.2:test
```

執行前需確保 `JAVA_HOME` 指向可正常啟動的 JDK 17 以上版本。

`PositionalIndexerTest` 驗證手算精確輸出、df/tf、位置空缺、Unicode 排序、單區塊與多區塊一致、多輪合併、空輸入、非法參數、I/O 失敗與 WARC 整合。測試產物保留於 `target/index-tests/run-*`。

正式 ClueWeb09 資料未包含在專案內，仍需以組別分配的資料量測執行時間與記憶體用量。
