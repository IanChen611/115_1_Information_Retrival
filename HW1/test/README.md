# 測試資料（負責：D）

- `docN.txt`：自製的小測試文件
- `expected_none.txt`：不開任何選項時，手算的 postings（格式同 `out/postings.txt`）
- `expected_casefold.txt`：開 `--casefold` 時的手算結果

程式完成後，將輸出與期望結果逐行比對；不一致時依問題回報：解析 → A、斷詞 → B、df/tf/位置 → C。
