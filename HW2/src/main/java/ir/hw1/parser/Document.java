package ir.hw1.parser;

import java.util.Objects;

/**
 * 一篇已解析完成的文件（A 產出 → B、C 使用）。
 *
 * @param docId  內部文件編號，從 1 開始、依讀取順序連續遞增（1, 2, 3, ...）
 * @param trecId WARC header 中的 WARC-TREC-ID，例如 "clueweb09-en0000-00-00000"
 * @param url    WARC header 中的 WARC-Target-URI，沒有則為空字串
 * @param text   去除 HTTP header 與 HTML 標籤（含 script/style）後的純文字本文
 */
public record Document(int docId, String trecId, String url, String text) {

    public Document {
        if (docId < 1) {
            throw new IllegalArgumentException("docId must start from 1, got " + docId);
        }
        Objects.requireNonNull(trecId, "trecId");
        url = (url == null) ? "" : url;
        text = (text == null) ? "" : text;
    }
}
