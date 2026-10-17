package ir.hw1.index;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.Closeable;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

/**
 * 中間區塊格式：magic/version，依 term 排序的 [UTF-8 term, df, postings]，最後以 -1 結束。
 * 每筆 posting 是 docId、tf 與 tf 個位置。先讀 df 即可合併，不需載入整個 term。
 */
final class IndexBlock {
    private static final int MAGIC = 0x49524831;
    private static final int VERSION = 1;
    private static final int BUFFER_SIZE = 64 * 1024;

    private IndexBlock() { }

    interface Sink {
        void startTerm(String term, int df) throws IOException;
        void startPosting(int docId, int tf) throws IOException;
        void position(int position) throws IOException;
        void endPosting() throws IOException;
        void endTerm() throws IOException;
    }

    static final class Writer implements Sink, Closeable {
        private final DataOutputStream output;

        Writer(Path path) throws IOException {
            output = new DataOutputStream(new BufferedOutputStream(
                    Files.newOutputStream(path, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE),
                    BUFFER_SIZE));
            try {
                output.writeInt(MAGIC);
                output.writeInt(VERSION);
            } catch (IOException e) {
                try { output.close(); } catch (IOException closeError) { e.addSuppressed(closeError); }
                throw e;
            }
        }

        @Override
        public void startTerm(String term, int df) throws IOException {
            byte[] bytes = term.getBytes(StandardCharsets.UTF_8);
            output.writeInt(bytes.length);
            output.write(bytes);
            output.writeInt(df);
        }

        @Override
        public void startPosting(int docId, int tf) throws IOException {
            output.writeInt(docId);
            output.writeInt(tf);
        }

        @Override
        public void position(int position) throws IOException {
            output.writeInt(position);
        }

        @Override
        public void endPosting() { }

        @Override
        public void endTerm() { }

        @Override
        public void close() throws IOException {
            // 結束標記讓 reader 能區分正常結尾與不完整的檔案。
            try (DataOutputStream stream = output) {
                stream.writeInt(-1);
            }
        }
    }

    static final class Reader implements Closeable {
        private final DataInputStream input;
        private String term;
        private int df;
        private int remainingPostings;

        Reader(Path path) throws IOException {
            input = new DataInputStream(new BufferedInputStream(Files.newInputStream(path), BUFFER_SIZE));
            try {
                if (input.readInt() != MAGIC || input.readInt() != VERSION) {
                    throw new IOException("Unsupported index block: " + path);
                }
                advance();
            } catch (IOException | RuntimeException e) {
                try { input.close(); } catch (IOException closeError) { e.addSuppressed(closeError); }
                throw e;
            }
        }

        boolean hasTerm() {
            return term != null;
        }

        String term() {
            return term;
        }

        int documentFrequency() {
            return df;
        }

        void copyPostings(Sink sink) throws IOException {
            int previousDocId = 0;
            while (remainingPostings > 0) {
                int docId = input.readInt();
                int tf = input.readInt();
                if (docId <= previousDocId || tf <= 0) {
                    throw new IOException("Invalid posting in index block for term: " + term);
                }
                sink.startPosting(docId, tf);
                int previousPosition = 0;
                for (int i = 0; i < tf; i++) {
                    int position = input.readInt();
                    if (position <= previousPosition) {
                        throw new IOException("Invalid position in index block for term: " + term);
                    }
                    sink.position(position);
                    previousPosition = position;
                }
                sink.endPosting();
                previousDocId = docId;
                remainingPostings--;
            }
        }

        void advance() throws IOException {
            if (remainingPostings != 0) {
                throw new IllegalStateException("Current term postings have not been consumed");
            }
            int length = input.readInt();
            if (length == -1) {
                if (input.read() != -1) {
                    throw new IOException("Trailing data after index block end marker");
                }
                term = null;
                df = 0;
                return;
            }
            if (length <= 0) {
                throw new IOException("Invalid term length in index block");
            }
            byte[] bytes = input.readNBytes(length);
            if (bytes.length != length) {
                throw new IOException("Truncated term in index block");
            }
            String nextTerm = new String(bytes, StandardCharsets.UTF_8);
            if (term != null && term.compareTo(nextTerm) >= 0) {
                throw new IOException("Terms must be strictly increasing in index block");
            }
            term = nextTerm;
            df = input.readInt();
            if (df <= 0) {
                throw new IOException("Invalid document frequency in index block");
            }
            remainingPostings = df;
        }

        @Override
        public void close() throws IOException {
            input.close();
        }
    }
}
