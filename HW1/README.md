# IR HW1 — Document Indexing（一頁說明文件，負責：D）

## 組員與分工
| 姓名 | 學號 | 負責部分 |
|------|------|---------|
|  |  | A：WARC 解析、主程式、skip pointers |
|  |  | B：Tokenizer、case folding / stemming、trie |
|  |  | C：Positional inverted index 建構與輸出 |
|  |  | D：停用詞表、測試驗證、說明文件 |

## 主要功能
（待填）

## Tokenizer 規則
 - 按空格與標點斷詞：主要依賴空格、換行符號、逗號、句號等標點符號來切分單字。
 - 智慧移除標點：在大多數情況下會去除標點符號，但會保留某些特定語境下的符號（例如單字內部的撇號 it's 或連字號）。
 - 中日韓（CJK）文字處理：對於中文、日文、韓文等不使用空格分隔的語言，StandardTokenizer 會自動採用 單字切分（One-Gram / Bi-gram） 的方式，也就是把每個中文字單獨切分成一個 Token。
 - 數位與英文字母：會保持連貫，例如 Lucene6.0 或 XYZ123 通常會被視為同一個 Token。
 - 電子郵件與網址：識別並完整保留電子郵件地址（如 abc@example.com）和網址（如 https://lucene.apache.org），而不會將其拆散。

## 執行環境與安裝
- JDK 17 以上、Maven 3.9 以上
- 相依套件（由 Maven 自動下載）：jsoup、lucene-analysis-common（測試用：JUnit 5）

## 資料集
到[這個連結](https://drive.google.com/drive/u/3/folders/1E0bOvsjDMTwakAtN_D250tLcuI7wgCEU)下載資料集，並放在這個位置：

```
115_1_Information_Retrival/
└── HW1/
    ├── data/                  ← 放這裡
    │   └── xxx.warc.gz
    ├── out/                   ← 程式輸出的索引會在這裡
    ├── pom.xml
    └── src/
```

> `data/` 與 `out/` 已在 `.gitignore` 中，資料集不會被 commit（單檔約 1GB，超過 GitHub 上限）。

## 編譯與執行
在 `HW1/` 目錄下執行：

```
mvn package
java -Xmx4g -jar target/hw1.jar --input data/10.warc.gz --output out/ [--casefold] [--stopword] [--stem]
```

## 輸出格式
- `out/dictionary.txt`：`term<TAB>df`，依 term 排序
- `out/postings.txt`：`term, df: <docId, tf: <pos1, pos2, ...>; ...>`
- `out/docs.txt`：`docId<TAB>TREC-ID<TAB>URL`

## 遇到的主要困難
（待填，每人提供自己負責部分的困難）
