package ir.hw1.index;

import java.io.BufferedWriter;
import java.io.Closeable;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.PriorityQueue;

/** 合併相鄰文件範圍的區塊，限制同時開啟的 reader，逐筆寫出而不累積整條 postings。 */
final class IndexMerger {
    private IndexMerger() { }

    static void finish(List<Path> blocks, Path workDirectory, Path outputDir, int fanIn) throws IOException {
        List<Path> current = new ArrayList<>(blocks);
        int pass = 0;
        while (current.size() > fanIn) {
            List<Path> next = new ArrayList<>();
            for (int start = 0; start < current.size(); start += fanIn) {
                List<Path> group = current.subList(start, Math.min(start + fanIn, current.size()));
                if (group.size() == 1) {
                    next.add(group.get(0));
                    continue;
                }
                Path merged = workDirectory.resolve("merge-" + pass + "-" + next.size() + ".bin");
                try (IndexBlock.Writer writer = new IndexBlock.Writer(merged)) {
                    merge(group, writer);
                }
                next.add(merged);
            }
            current = next;
            pass++;
        }
        try (BufferedWriter dictionary = Files.newBufferedWriter(
                     outputDir.resolve("dictionary.txt"), StandardCharsets.UTF_8);
             BufferedWriter postings = Files.newBufferedWriter(
                     outputDir.resolve("postings.txt"), StandardCharsets.UTF_8)) {
            merge(current, new TextSink(dictionary, postings));
        }
    }

    private static void merge(List<Path> paths, IndexBlock.Sink sink) throws IOException {
        try (Readers readers = new Readers(paths)) {
            PriorityQueue<Cursor> queue = new PriorityQueue<>(Comparator
                    .comparing((Cursor cursor) -> cursor.reader.term())
                    .thenComparingInt(cursor -> cursor.order));
            for (int i = 0; i < readers.values.size(); i++) {
                IndexBlock.Reader reader = readers.values.get(i);
                if (reader.hasTerm()) {
                    queue.add(new Cursor(i, reader));
                }
            }
            while (!queue.isEmpty()) {
                String term = queue.peek().reader.term();
                List<Cursor> matching = new ArrayList<>();
                long df = 0;
                while (!queue.isEmpty() && term.equals(queue.peek().reader.term())) {
                    Cursor cursor = queue.remove();
                    matching.add(cursor);
                    df += cursor.reader.documentFrequency();
                }
                if (df > Integer.MAX_VALUE) {
                    throw new IOException("Document frequency exceeds supported docId range: " + term);
                }
                // order 是不重疊且連續的文件範圍順序，所以 df 可相加、postings 可直接串接。
                matching.sort(Comparator.comparingInt(cursor -> cursor.order));
                sink.startTerm(term, (int) df);
                for (Cursor cursor : matching) {
                    cursor.reader.copyPostings(sink);
                    cursor.reader.advance();
                    if (cursor.reader.hasTerm()) {
                        queue.add(cursor);
                    }
                }
                sink.endTerm();
            }
        }
    }

    private record Cursor(int order, IndexBlock.Reader reader) { }

    /** 開啟失敗時也必須關閉已開啟的 readers。 */
    private static final class Readers implements Closeable {
        private final List<IndexBlock.Reader> values = new ArrayList<>();

        Readers(List<Path> paths) throws IOException {
            try {
                for (Path path : paths) {
                    values.add(new IndexBlock.Reader(path));
                }
            } catch (IOException | RuntimeException e) {
                try { close(); } catch (IOException closeError) { e.addSuppressed(closeError); }
                throw e;
            }
        }

        @Override
        public void close() throws IOException {
            IOException failure = null;
            for (IndexBlock.Reader reader : values) {
                try {
                    reader.close();
                } catch (IOException e) {
                    if (failure == null) {
                        failure = e;
                    } else {
                        failure.addSuppressed(e);
                    }
                }
            }
            if (failure != null) {
                throw failure;
            }
        }
    }

    private static final class TextSink implements IndexBlock.Sink {
        private final BufferedWriter dictionary;
        private final BufferedWriter postings;
        private boolean firstPosting;
        private boolean firstPosition;

        TextSink(BufferedWriter dictionary, BufferedWriter postings) {
            this.dictionary = dictionary;
            this.postings = postings;
        }

        @Override
        public void startTerm(String term, int df) throws IOException {
            dictionary.write(term);
            dictionary.write('\t');
            dictionary.write(Integer.toString(df));
            dictionary.newLine();
            postings.write(term);
            postings.write(", ");
            postings.write(Integer.toString(df));
            postings.write(": <");
            firstPosting = true;
        }

        @Override
        public void startPosting(int docId, int tf) throws IOException {
            if (!firstPosting) {
                postings.write("; ");
            }
            firstPosting = false;
            postings.write(Integer.toString(docId));
            postings.write(", ");
            postings.write(Integer.toString(tf));
            postings.write(": <");
            firstPosition = true;
        }

        @Override
        public void position(int position) throws IOException {
            if (!firstPosition) {
                postings.write(", ");
            }
            firstPosition = false;
            postings.write(Integer.toString(position));
        }

        @Override
        public void endPosting() throws IOException {
            postings.write('>');
        }

        @Override
        public void endTerm() throws IOException {
            postings.write('>');
            postings.newLine();
        }
    }
}
