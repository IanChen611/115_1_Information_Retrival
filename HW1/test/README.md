# 測試資料與驗證指南（負責：D）

本目錄包含由組員 D 手動建立的小型測試文本與手算標準答案（Ground Truth），專供驗證整個 Pipeline（包含斷詞、位置編號、df/tf 計算、大小寫轉換、停用詞過濾、Stemming 與倒排索引輸出）之正確性。

---

## 1. 測試文件清單

| 檔案 | 內容 | 說明 |
| :--- | :--- | :--- |
| `doc1.txt` | `Information retrieval is fun.` | 測試大小寫混用、常見停用詞 `is` |
| `doc2.txt` | `Retrieval models search information.` | 測試複數型態 `models`、與 doc1 大小寫對比 |
| `doc3.txt` | `Search and retrieval are fun.` | 測試多停用詞 `and`, `are`、多重複詞 `retrieval`, `fun` |
| `doc4.txt` | `Information systems search retrieval.` | 測試複數 `systems`、多重重疊詞彙 |

---

## 2. 手算期望輸出（Expected Outputs）

### 模式 A：無參數（預設，區分大小寫、不停用詞、不詞幹提取）
- 倒排清單：`expected_none.txt`
- 詞彙字典：`expected_none_dict.txt`
- 排序規則：依 Java `String.compareTo` 排序（ASCII 大寫字母優先於小寫字母）。

### 模式 B：開啟 `--casefold`（大小寫正規化）
- 倒排清單：`expected_casefold.txt`
- 詞彙字典：`expected_casefold_dict.txt`
- 特色：`Information` 與 `information` 合併為 `information`（df 累加為 3）。

### 模式 C：開啟 `--casefold --stopword --stem`（全功能開啟）
- 倒排清單：`expected_all.txt`
- 詞彙字典：`expected_all_dict.txt`
- 特色：
  - 停用詞 `is`, `and`, `are` 被過濾，但後續詞彙的位置編號（position）保持原順序空缺（例：`doc1.txt` 中 `fun` 保留 position 4）。
  - `models` → `model`, `systems` → `system`, `retrieval` → `retriev`, `information` → `inform`。

---

## 3. 測試比對與問題回報流程

程式完成執行後，將產生的 `out/postings.txt` 與本目錄的手算檔案逐行比對：

1. **若詞彙切分不正確、缺少單字或標點符號未移除**：
   👉 回報給 **組員 B**（`DefaultTokenizer.java`、斷詞規則）
2. **若停用詞未過濾、過濾錯誤或 stopwords.txt 遺失**：
   👉 回報給 **組員 D**（`StopwordList.java`、`stopwords.txt`）
3. **若 df、tf 計算錯誤、docId 未依序排列、位置遺漏或 dictionary/postings 格式不符**：
   👉 回報給 **組員 C**（`PositionalIndexer.java`、`IndexMerger.java`）
4. **若 WARC 解析錯誤、HTML 標籤未清洗乾淨或命令列參數串接有誤**：
   👉 回報給 **組員 A**（`WarcReader.java`、`Main.java`）
