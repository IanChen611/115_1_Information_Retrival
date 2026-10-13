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
（數字、連字號、標點、撇號的處理方式，待和 B 確認後填寫）

## 執行環境與安裝
- JDK 17 以上、Maven 3.9 以上
- 相依套件（由 Maven 自動下載）：jwarc、jsoup、lucene-analysis-common

```
mvn package
java -Xmx4g -jar target/hw1.jar --input <file.warc.gz> --output out/ [--casefold] [--stopword] [--stem]
```

## 輸出格式
- `out/dictionary.txt`：`term<TAB>df`，依 term 排序
- `out/postings.txt`：`term, df: <docId, tf: <pos1, pos2, ...>; ...>`
- `out/docs.txt`：`docId<TAB>TREC-ID<TAB>URL`

## 遇到的主要困難
（待填，每人提供自己負責部分的困難）
