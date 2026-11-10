package ir.hw1.text;

import java.util.Objects;

/**
 * 一個詞在文件本文中的一次出現（B 產出 → C 使用）。
 *
 * @param term     經過正規化後的詞（依設定可能已轉小寫、已 stemming），不可為空字串
 * @param position 該詞在文件中的位置，從 1 開始。被移除的停用詞仍佔一個位置，
 *                 因此同一篇文件產出的 position 可能不連續（例如 1, 2, 4, 5）
 */
public record Token(String term, int position) {

    public Token {
        Objects.requireNonNull(term, "term");
        if (term.isEmpty()) {
            throw new IllegalArgumentException("term must not be empty");
        }
        if (position < 1) {
            throw new IllegalArgumentException("position must start from 1, got " + position);
        }
    }
}
