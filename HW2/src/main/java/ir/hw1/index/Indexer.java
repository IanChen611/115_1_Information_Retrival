package ir.hw1.index;

import ir.hw1.text.Token;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

/**
 * 建立 positional inverted index。（介面：共同；實作：C）
 */
public interface Indexer {

    /**
     * 加入一篇文件的所有 token。
     *
     * @param docId  正整數文件編號，呼叫順序保證嚴格遞增，可有間隔
     * @param tokens 依 position 嚴格遞增排序的 token，可能為空列表；保留停用詞造成的位置空缺
     */
    void addDocument(int docId, List<Token> tokens) throws IOException;

    /**
     * 所有文件加入完畢後呼叫，將索引寫到 outputDir。
     *
     * <p><b>dictionary.txt</b>：依 term 排序（{@link String#compareTo}），一行一個，
     * 格式為 {@code term<TAB>df}
     *
     * <p><b>postings.txt</b>：依 term 排序，一行一個 term，docId 與 position 皆遞增：
     * <pre>
     * term, df: &lt;docId, tf: &lt;pos1, pos2, ...&gt;; docId, tf: &lt;pos1, ...&gt;&gt;
     * </pre>
     * 例：{@code sat, 2: <1, 1: <3>; 2, 1: <3>>}
     */
    void finish(Path outputDir) throws IOException;
}
