"""
HW1 測試比對工具（負責：D）
用於將程式實際產出的 postings.txt 與手算的 expected_*.txt 進行逐行比對。

使用方式：
    python test/verify.py --output out/postings.txt --expected test/expected_none.txt
"""

import argparse
import sys

def compare_files(output_path, expected_path):
    print(f"[*] Comparing: {output_path} vs {expected_path}")
    try:
        with open(output_path, "r", encoding="utf-8") as f1, open(expected_path, "r", encoding="utf-8") as f2:
            out_lines = [l.strip() for l in f1 if l.strip()]
            exp_lines = [l.strip() for l in f2 if l.strip()]
    except FileNotFoundError as e:
        print(f"[!] 找不到檔案: {e}")
        return False

    if len(out_lines) != len(exp_lines):
        print(f"[X] 行數不一致！實際產出: {len(out_lines)} 行，期望: {len(exp_lines)} 行")

    max_lines = max(len(out_lines), len(exp_lines))
    mismatches = 0
    for idx in range(max_lines):
        out_line = out_lines[idx] if idx < len(out_lines) else "<EOF>"
        exp_line = exp_lines[idx] if idx < len(exp_lines) else "<EOF>"
        if out_line != exp_line:
            mismatches += 1
            print(f"\n[差異 第 {idx + 1} 行]")
            print(f"  實際: {out_line}")
            print(f"  期望: {exp_line}")

    if mismatches == 0:
        print("[O] 比對成功！所有輸出皆與手算標準答案完全相符。")
        return True
    else:
        print(f"\n[X] 比對失敗，共發現 {mismatches} 處不一致。")
        print("    除錯指引：")
        print("      - 詞彙未出現或符號殘留 -> 回報組員 B (Tokenizer)")
        print("      - 停用詞未排除 -> 回報組員 D (StopwordList)")
        print("      - df / tf / 位置編號或排序不合 -> 回報組員 C (Indexer)")
        return False

if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="比對 Postings 輸出工具")
    parser.add_argument("--output", default="out/postings.txt", help="實際產出的 postings 檔案路徑")
    parser.add_argument("--expected", default="test/expected_none.txt", help="期望的手算 postings 檔案路徑")
    args = parser.parse_args()

    success = compare_files(args.output, args.expected)
    sys.exit(0 if success else 1)
