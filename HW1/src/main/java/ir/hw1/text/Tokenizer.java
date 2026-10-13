package ir.hw1.text;

import java.util.List;

/**
 * 把一篇文件的純文字切成 Token（B 實作，例如 DefaultTokenizer）。
 *
 * <p>規則（實作者需在說明文件中寫清楚細節）：
 * <ul>
 *   <li>position 從 1 開始，每切出一個詞就 +1</li>
 *   <li>被停用詞過濾掉的詞：不回傳，但仍佔用 position</li>
 *   <li>數字、連字號、標點、撇號的處理方式由 B 決定並記錄</li>
 * </ul>
 * 實作需為無狀態（或執行緒安全），同一個 Tokenizer 物件會被重複用於所有文件。
 */
public interface Tokenizer {

    /**
     * @param text 文件純文字（即 {@code Document.text()}）
     * @return 依 position 遞增排序的 Token 列表；沒有任何詞時回傳空列表，不可回傳 null
     */
    List<Token> tokenize(String text);
}
