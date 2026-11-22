package cc.avie.ferrum.natives;

import java.lang.ref.Cleaner;

/**
 * A native inflate stream for zlib streams, reused across calls.
 *
 * <p>Instances are not thread-safe. {@link #close()} frees the native state immediately; otherwise it is freed once
 * the instance becomes unreachable.
 */
public final class ZlibInflater implements AutoCloseable {
    private final long handle;
    private final Cleaner.Cleanable cleanable;
    private boolean closed;

    public ZlibInflater() {
        long handle = NativeZlib.newInflater();
        this.handle = handle;
        this.cleanable = NativeZlib.CLEANER.register(this, () -> NativeZlib.freeInflater(handle));
    }

    /**
     * Decompresses the zlib stream at {@code src} into at most {@code dstCapacity} bytes at {@code dst}.
     *
     * @return the number of bytes written, or {@link NativeZlib#ERR_DATA} if the stream is malformed
     */
    public long inflate(long src, long srcLength, long dst, long dstCapacity) {
        if (this.closed) {
            throw new IllegalStateException("Inflater is closed");
        }
        return NativeZlib.inflate(this.handle, src, srcLength, dst, dstCapacity);
    }

    /**
     * Decompresses a zlib stream of unknown size; see {@link NativeZlib#inflateStream}.
     */
    public long inflateStream(long src, long srcLength, long dst, long dstCapacity, boolean reset) {
        if (this.closed) {
            throw new IllegalStateException("Inflater is closed");
        }
        return NativeZlib.inflateStream(this.handle, src, srcLength, dst, dstCapacity, reset);
    }

    @Override
    public void close() {
        this.closed = true;
        this.cleanable.clean();
    }
}
