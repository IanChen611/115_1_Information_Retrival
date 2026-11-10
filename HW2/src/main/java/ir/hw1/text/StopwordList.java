package ir.hw1.text;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * 停用詞表。（負責：D）
 *
 * <p>從 classpath 上的 {@code stopwords.txt}（位於 src/main/resources/）讀入：
 * 一行一個詞，忽略空行與 # 開頭的註解行，全部轉小寫後存進 {@code HashSet<String>}。
 *
 * <p>提示：用 {@code StopwordList.class.getResourceAsStream("/stopwords.txt")} 開檔，
 * 再用 {@code BufferedReader} 搭配 {@code StandardCharsets.UTF_8} 逐行讀取。
 */
public class StopwordList {

    private static volatile StopwordList instance;
    private final Set<String> stopwords;

    private StopwordList(Set<String> stopwords) {
        this.stopwords = Collections.unmodifiableSet(stopwords);
    }

    /** 讀入 resources/stopwords.txt。 */
    public static StopwordList loadDefault() {
        if (instance == null) {
            synchronized (StopwordList.class) {
                if (instance == null) {
                    Set<String> words = new HashSet<>();
                    try (InputStream in = StopwordList.class.getResourceAsStream("/stopwords.txt")) {
                        if (in == null) {
                            throw new IllegalStateException("Resource /stopwords.txt not found on classpath");
                        }
                        try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                            String line;
                            while ((line = reader.readLine()) != null) {
                                line = line.trim();
                                if (line.isEmpty() || line.startsWith("#")) {
                                    continue;
                                }
                                words.add(line.toLowerCase());
                            }
                        }
                    } catch (IOException e) {
                        throw new UncheckedIOException("Failed to read stopwords.txt", e);
                    }
                    instance = new StopwordList(words);
                }
            }
        }
        return instance;
    }

    /** word 是否為停用詞（不分大小寫）。 */
    public boolean contains(String word) {
        if (word == null) {
            return false;
        }
        return stopwords.contains(word.toLowerCase());
    }

    /** 停用詞數量。 */
    public int size() {
        return stopwords.size();
    }
}

