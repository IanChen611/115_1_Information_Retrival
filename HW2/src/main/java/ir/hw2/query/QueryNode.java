package ir.hw2.query;

import java.util.List;

/**
 * 查詢的語法樹節點。（工作二）
 *
 * <p>節點內的 term 都已經過與建索引相同的 tokenizer 正規化。
 */
public sealed interface QueryNode {

    /** 單一詞。 */
    record Term(String term) implements QueryNode {
    }

    /** 前綴萬用字元 {@code retriev*}。 */
    record Prefix(String prefix) implements QueryNode {
    }

    /**
     * 片語 {@code "open source"}。
     *
     * @param offsets 各詞相對第一個詞的位置差（被移除的停用詞仍佔位置）
     */
    record Phrase(List<String> terms, List<Integer> offsets) implements QueryNode {
    }

    /** 鄰近查詢 {@code a /k b}：兩詞位置差不超過 k（不分先後）。 */
    record Proximity(String left, String right, int distance) implements QueryNode {
    }

    record And(List<QueryNode> children) implements QueryNode {
    }

    record Or(List<QueryNode> children) implements QueryNode {
    }

    record Not(QueryNode child) implements QueryNode {
    }
}
