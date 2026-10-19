import os
import re
import sys

def audit_dir(dir_name):
    print(f"=== 檢查目錄: {dir_name} ===")
    dict_path = os.path.join(dir_name, "dictionary.txt")
    post_path = os.path.join(dir_name, "postings.txt")
    docs_path = os.path.join(dir_name, "docs.txt")

    for p in [dict_path, post_path, docs_path]:
        if not os.path.exists(p):
            print(f"  [X] 缺少必要檔案: {p}")
            return
        size_mb = os.path.getsize(p) / (1024 * 1024)
        print(f"  [V] 檔案存在: {os.path.basename(p)} ({size_mb:.2f} MB)")

    # 1. 檢查 docs.txt
    with open(docs_path, "r", encoding="utf-8", errors="ignore") as f:
        doc_lines = [line.strip() for line in f if line.strip()]
    print(f"  [文件數] 成功索引篇數: {len(doc_lines):,} 篇")
    if doc_lines:
        first_doc = doc_lines[0].split("\t")
        last_doc = doc_lines[-1].split("\t")
        print(f"    第 1 篇: docId={first_doc[0]}, TREC-ID={first_doc[1]}, URL={first_doc[2][:50]}...")
        print(f"    最終篇:  docId={last_doc[0]}, TREC-ID={last_doc[1]}, URL={last_doc[2][:50]}...")

    # 2. 檢查 dictionary.txt
    dict_terms = []
    dict_dfs = []
    with open(dict_path, "r", encoding="utf-8", errors="ignore") as f:
        for idx, line in enumerate(f):
            parts = line.rstrip("\r\n").split("\t")
            if len(parts) != 2:
                print(f"  [X] dictionary.txt 第 {idx+1} 行格式錯誤（非 tab 分隔）: {line[:40]}")
                break
            dict_terms.append(parts[0])
            dict_dfs.append(int(parts[1]))

    print(f"  [詞彙量] 獨立詞項總數 (Vocabulary): {len(dict_terms):,} 個詞")

    # 檢查 dictionary 排序 (嚴格遞增檢查)
    sort_inversions = 0
    for i in range(min(len(dict_terms) - 1, 100000)):
        if dict_terms[i] >= dict_terms[i+1]:
            sort_inversions += 1
            if sort_inversions <= 3:
                print(f"    排序警示: '{dict_terms[i]}' >= '{dict_terms[i+1]}'")
    if sort_inversions == 0:
        print("  [排序檢查] dictionary.txt 字典序嚴格遞增: 100% 正確")
    else:
        print(f"  [排序檢查] 發現 {sort_inversions} 處逆序")

    # 3. 檢查 postings.txt 格式與內部一致性
    post_pattern = re.compile(r"^(.+?), (\d+): <(.+)>$")
    doc_entry_pattern = re.compile(r"^(\d+), (\d+): <([\d, ]+)>$")

    post_count = 0
    format_errors = 0
    tf_mismatches = 0
    df_mismatches = 0
    term_mismatches = 0

    with open(post_path, "r", encoding="utf-8", errors="ignore") as f:
        for idx, line in enumerate(f):
            post_count += 1
            line = line.rstrip("\r\n")

            # 抽樣詳細檢查 (前 5000 行以及後續每 20000 行抽樣)
            if idx < 5000 or idx % 20000 == 0:
                m = post_pattern.match(line)
                if not m:
                    format_errors += 1
                    if format_errors <= 3:
                        print(f"  [X] postings.txt 格式錯誤 (行 {idx+1}): {line[:60]}")
                    continue

                term, df_val, body = m.group(1), int(m.group(2)), m.group(3)

                # 比對與 dictionary 中的詞項與 df
                if idx < len(dict_terms):
                    if term != dict_terms[idx]:
                        term_mismatches += 1
                        if term_mismatches <= 3:
                            print(f"  [X] postings 與 dictionary 詞項不一致 (行 {idx+1}): '{term}' vs '{dict_terms[idx]}'")
                    if df_val != dict_dfs[idx]:
                        df_mismatches += 1
                        if df_mismatches <= 3:
                            print(f"  [X] postings 與 dictionary df 不一致 (行 {idx+1}): {df_val} vs {dict_dfs[idx]}")

                # 檢查每篇 doc 格式與 tf、pos 遞增
                doc_entries = body.split("; ")
                if len(doc_entries) != df_val:
                    df_mismatches += 1
                    if df_mismatches <= 3:
                        print(f"  [X] df 與文件清單長度不合 (行 {idx+1}): df={df_val}, 實際有 {len(doc_entries)} 筆")

                prev_doc_id = 0
                for d_entry in doc_entries:
                    dm = doc_entry_pattern.match(d_entry)
                    if not dm:
                        format_errors += 1
                        if format_errors <= 3:
                            print(f"  [X] doc posting 格式錯誤: {d_entry[:40]}")
                        break
                    doc_id, tf_val, pos_str = int(dm.group(1)), int(dm.group(2)), dm.group(3)
                    if doc_id <= prev_doc_id:
                        format_errors += 1
                        if format_errors <= 3:
                            print(f"  [X] docId 未嚴格遞增: {doc_id} <= {prev_doc_id}")
                    prev_doc_id = doc_id

                    positions = [int(p) for p in pos_str.split(", ")]
                    if len(positions) != tf_val:
                        tf_mismatches += 1
                        if tf_mismatches <= 3:
                            print(f"  [X] tf 與位置數量不合: tf={tf_val}, 位置有 {len(positions)} 個")

                    # 檢查 positions 嚴格遞增
                    for p_i in range(len(positions) - 1):
                        if positions[p_i] >= positions[p_i+1]:
                            format_errors += 1
                            if format_errors <= 3:
                                print(f"  [X] position 未嚴格遞增: {positions[p_i]} >= {positions[p_i+1]}")
                            break

    print(f"  [Postings 總行數] {post_count:,} 行")
    print(f"  [行數完全對齊] dictionary 行數 == postings 行數: {len(dict_terms) == post_count}")
    print(f"  [格式與規格檢查] 錯誤數: format_errors={format_errors}, df_mismatch={df_mismatches}, tf_mismatch={tf_mismatches}")
    if format_errors == 0 and df_mismatches == 0 and tf_mismatches == 0:
        print("  => 結果：100% 符合作業規格！\n")
    else:
        print("  => 結果：發現部分不符合規格處，請檢查！\n")

if __name__ == "__main__":
    for d in ["out", "out_casefold", "out_all"]:
        if os.path.exists(d):
            audit_dir(d)
