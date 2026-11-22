package cc.avie.ferrum.storage;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.Objects;

/**
 * Collects the uncompressed chunk data and writes it to the wrapped stream as one zlib stream when closed.
 */
final class DeflatingOutputStream extends OutputStream {
    private final OutputStream out;
    private byte[] buffer = new byte[32 << 10];
    private int count;
    private boolean closed;

    DeflatingOutputStream(OutputStream out) {
        this.out = out;
    }

    @Override
    public void write(int b) throws IOException {
        this.ensureOpen();
        this.ensureCapacity(this.count + 1);
        this.buffer[this.count++] = (byte) b;
    }

    @Override
    public void write(byte[] b, int off, int len) throws IOException {
        Objects.checkFromIndexSize(off, len, b.length);
        this.ensureOpen();
        this.ensureCapacity(this.count + len);
        System.arraycopy(b, off, this.buffer, this.count, len);
        this.count += len;
    }

    @Override
    public void close() throws IOException {
        if (this.closed) {
            return;
        }
        this.closed = true;
        try (this.out) {
            RegionCompression.deflate(this.buffer, this.count, this.out);
        }
    }

    private void ensureOpen() throws IOException {
        if (this.closed) {
            throw new IOException("Stream closed");
        }
    }

    private void ensureCapacity(int capacity) {
        if (capacity < 0) {
            throw new OutOfMemoryError("Chunk data is too large");
        }
        if (capacity > this.buffer.length) {
            this.buffer = Arrays.copyOf(this.buffer, Math.max(capacity, this.buffer.length * 2));
        }
    }
}
