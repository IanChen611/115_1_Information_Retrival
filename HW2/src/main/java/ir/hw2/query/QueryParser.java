package ir.hw2.query;

import ir.hw1.text.Token;
import ir.hw1.text.Tokenizer;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.UnaryOperator;

/**
 * 把查詢字串解析成 {@link QueryNode}。（工作二）
 *
 * <pre>
 * query   := or
 * or      := and ( "OR" and )*
 * and     := not ( "AND" not )*
 * not     := "NOT" not | group
 * group   := primary+                      相鄰的詞（自由文字）
 * primary := "(" or ")" | "片語" | word "/k" word | word"*" | word
 * </pre>
 *
 * <p>運算子必須大寫。自由文字的語意依所在位置而定：
 * <ul>
 *   <li>整個查詢就是一串詞（{@code hong kong business}）→ 任一詞命中即可（OR），交給排序決定名次</li>
 *   <li>作為布林運算元或在括號內（{@code open source AND linux}）→ 該運算元的詞必須全部出現（AND）</li>
 * </ul>
 */
public final class QueryParser {

    private enum Type { LPAREN, RPAREN, AND, OR, NOT, PHRASE, PROXIMITY, WORD }

    private record Lexeme(Type type, String text) {
    }

    /** 尚未決定語意的自由文字群組，只在解析過程中出現。 */
    private record Group(List<QueryNode> children) {
    }

    private final Tokenizer tokenizer;
    private final UnaryOperator<String> termFixer;
    private List<Lexeme> lexemes;
    private int next;

    /**
     * @param tokenizer 與建索引相同設定的 tokenizer
     * @param termFixer 對每個正規化後的詞做最後修正（拼字校正）；不需要時傳 {@code UnaryOperator.identity()}
     */
    public QueryParser(Tokenizer tokenizer, UnaryOperator<String> termFixer) {
        this.tokenizer = tokenizer;
        this.termFixer = termFixer;
    }

    /** @return 語法樹；查詢正規化後沒有任何詞（例如全是停用詞）時回傳 null */
    public QueryNode parse(String query) {
        lexemes = lex(query);
        next = 0;
        if (lexemes.isEmpty()) {
            return null;
        }
        Object root = parseOr();
        if (next < lexemes.size()) {
            throw new QuerySyntaxException("多餘的 '" + lexemes.get(next).text() + "'");
        }
        if (root instanceof Group group) {
            return new QueryNode.Or(group.children());
        }
        return (QueryNode) root;
    }

    private Object parseOr() {
        List<QueryNode> children = new ArrayList<>();
        Object first = parseAnd();
        if (peek() != Type.OR) {
            return first;
        }
        addOperand(children, first);
        while (peek() == Type.OR) {
            next++;
            addOperand(children, parseAnd());
        }
        return collapse(children, true);
    }

    private Object parseAnd() {
        List<QueryNode> children = new ArrayList<>();
        Object first = parseNot();
        if (peek() != Type.AND) {
            return first;
        }
        addOperand(children, first);
        while (peek() == Type.AND) {
            next++;
            addOperand(children, parseNot());
        }
        return collapse(children, false);
    }

    private Object parseNot() {
        if (peek() == Type.NOT) {
            next++;
            QueryNode child = asOperand(parseNot());
            return child == null ? null : new QueryNode.Not(child);
        }
        return parseGroup();
    }

    private Object parseGroup() {
        List<QueryNode> children = new ArrayList<>();
        boolean consumed = false;
        while (peek() == Type.LPAREN || peek() == Type.PHRASE || peek() == Type.WORD) {
            parsePrimary(children);
            consumed = true;
        }
        if (!consumed) {
            String found = next < lexemes.size() ? "'" + lexemes.get(next).text() + "'" : "查詢結尾";
            throw new QuerySyntaxException("此處應為查詢詞，卻遇到 " + found);
        }
        if (children.isEmpty()) {
            return null;
        }
        return children.size() == 1 ? children.get(0) : new Group(children);
    }

    private void parsePrimary(List<QueryNode> out) {
        Lexeme lexeme = lexemes.get(next++);
        switch (lexeme.type()) {
            case LPAREN -> {
                QueryNode inner = asOperand(parseOr());
                if (peek() != Type.RPAREN) {
                    throw new QuerySyntaxException("缺少右括號 ')'");
                }
                next++;
                if (inner != null) {
                    out.add(inner);
                }
            }
            case PHRASE -> {
                List<Token> tokens = tokenizer.tokenize(lexeme.text());
                if (tokens.size() == 1) {
                    out.add(new QueryNode.Term(termFixer.apply(tokens.get(0).term())));
                } else if (tokens.size() > 1) {
                    List<String> terms = new ArrayList<>();
                    List<Integer> offsets = new ArrayList<>();
                    for (Token token : tokens) {
                        terms.add(termFixer.apply(token.term()));
                        offsets.add(token.position() - tokens.get(0).position());
                    }
                    out.add(new QueryNode.Phrase(terms, offsets));
                }
            }
            default -> {
                if (peek() == Type.PROXIMITY) {
                    int distance = Integer.parseInt(lexemes.get(next++).text().substring(1));
                    if (peek() != Type.WORD) {
                        throw new QuerySyntaxException("鄰近運算子 /" + distance + " 右邊應為查詢詞");
                    }
                    List<Token> left = tokenizer.tokenize(lexeme.text());
                    List<Token> right = tokenizer.tokenize(lexemes.get(next++).text());
                    if (!left.isEmpty() && !right.isEmpty()) {
                        out.add(new QueryNode.Proximity(termFixer.apply(left.get(left.size() - 1).term()),
                                termFixer.apply(right.get(0).term()), distance));
                    } else {
                        for (Token token : left.isEmpty() ? right : left) {
                            out.add(new QueryNode.Term(termFixer.apply(token.term())));
                        }
                    }
                } else if (lexeme.text().length() > 1 && lexeme.text().endsWith("*")) {
                    // 前綴不做 stemming，只轉小寫後與詞典比對
                    String prefix = lexeme.text().substring(0, lexeme.text().length() - 1);
                    out.add(new QueryNode.Prefix(prefix.toLowerCase(Locale.ROOT)));
                } else {
                    // non-profit 這類會被切成多個詞，逐一加入
                    for (Token token : tokenizer.tokenize(lexeme.text())) {
                        out.add(new QueryNode.Term(termFixer.apply(token.term())));
                    }
                }
            }
        }
    }

    /** 布林運算元或括號內的自由文字：全部詞都要出現。 */
    private static QueryNode asOperand(Object parsed) {
        if (parsed instanceof Group group) {
            return new QueryNode.And(group.children());
        }
        return (QueryNode) parsed;
    }

    private static void addOperand(List<QueryNode> children, Object parsed) {
        QueryNode node = asOperand(parsed);
        if (node != null) {
            children.add(node);
        }
    }

    private static QueryNode collapse(List<QueryNode> children, boolean or) {
        if (children.isEmpty()) {
            return null;
        }
        if (children.size() == 1) {
            return children.get(0);
        }
        return or ? new QueryNode.Or(children) : new QueryNode.And(children);
    }

    private Type peek() {
        return next < lexemes.size() ? lexemes.get(next).type() : null;
    }

    private static List<Lexeme> lex(String query) {
        List<Lexeme> out = new ArrayList<>();
        int i = 0;
        while (i < query.length()) {
            char c = query.charAt(i);
            if (Character.isWhitespace(c)) {
                i++;
            } else if (c == '(') {
                out.add(new Lexeme(Type.LPAREN, "("));
                i++;
            } else if (c == ')') {
                out.add(new Lexeme(Type.RPAREN, ")"));
                i++;
            } else if (c == '"') {
                int end = query.indexOf('"', i + 1);
                if (end < 0) {
                    throw new QuerySyntaxException("片語缺少結尾的雙引號");
                }
                out.add(new Lexeme(Type.PHRASE, query.substring(i + 1, end)));
                i = end + 1;
            } else {
                int start = i;
                while (i < query.length() && !Character.isWhitespace(query.charAt(i))
                        && "()\"".indexOf(query.charAt(i)) < 0) {
                    i++;
                }
                String word = query.substring(start, i);
                Type type = switch (word) {
                    case "AND" -> Type.AND;
                    case "OR" -> Type.OR;
                    case "NOT" -> Type.NOT;
                    default -> word.matches("/\\d{1,6}") ? Type.PROXIMITY : Type.WORD;
                };
                out.add(new Lexeme(type, word));
            }
        }
        return out;
    }
}
