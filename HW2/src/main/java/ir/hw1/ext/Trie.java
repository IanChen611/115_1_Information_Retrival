package ir.hw1.ext;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/**
 * 以 Trie 儲存詞彙，支援精確查詢與前綴查詢。
 */
public class Trie {

    private final Node root = new Node();

    /** 加入一個 term。 */
    public void insert(String term) {
        Node node = root;
        for (int i = 0; i < term.length(); i++) {
            node = node.children.computeIfAbsent(term.charAt(i), ignored -> new Node());
        }
        node.terminal = true;
    }

    /** term 是否存在。 */
    public boolean contains(String term) {
        Node node = findNode(term);
        return node != null && node.terminal;
    }

    /** 以 prefix 開頭的所有 term，依字典序排列（可用於萬用字元查詢）。 */
    public List<String> startsWith(String prefix) {
        Objects.requireNonNull(prefix, "prefix");
        Node node = findNode(prefix);
        List<String> terms = new ArrayList<>();
        if (node != null) {
            collectTerms(node, new StringBuilder(prefix), terms);
        }
        return terms;
    }

    private Node findNode(String text) {
        Node node = root;
        for (int i = 0; i < text.length(); i++) {
            node = node.children.get(text.charAt(i));
            if (node == null) {
                return null;
            }
        }
        return node;
    }

    private void collectTerms(Node node, StringBuilder term, List<String> terms) {
        if (node.terminal) {
            terms.add(term.toString());
        }
        for (Map.Entry<Character, Node> child : node.children.entrySet()) {
            term.append(child.getKey());
            collectTerms(child.getValue(), term, terms);
            term.setLength(term.length() - 1);
        }
    }

    private static final class Node {
        private final Map<Character, Node> children = new TreeMap<>();
        private boolean terminal;
    }
}
