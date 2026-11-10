package ir.hw2.query;

/** 查詢字串不合語法。（工作二） */
public class QuerySyntaxException extends IllegalArgumentException {

    public QuerySyntaxException(String message) {
        super(message);
    }
}
