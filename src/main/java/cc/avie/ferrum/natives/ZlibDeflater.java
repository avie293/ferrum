package cc.avie.ferrum.natives;

import java.lang.ref.Cleaner;

/**
 * A native deflate stream producing complete zlib streams, reused across calls.
 *
 * <p>Instances are not thread-safe. {@link #close()} frees the native state immediately; otherwise it is freed once
 * the instance becomes unreachable.
 */
public final class ZlibDeflater implements AutoCloseable {
    private final long handle;
    private final Cleaner.Cleanable cleanable;
    private boolean closed;

    /**
     * @param level the zlib compression level, {@code -1} for the default level
     */
    public ZlibDeflater(int level) {
        long handle = NativeZlib.newDeflater(level);
        this.handle = handle;
        this.cleanable = NativeZlib.CLEANER.register(this, () -> NativeZlib.freeDeflater(handle));
    }

    /**
     * Compresses {@code srcLength} bytes at {@code src} into {@code dst}.
     *
     * @return the length of the zlib stream, or {@link NativeZlib#ERR_BUFFER} if it does not fit
     */
    public long deflate(long src, long srcLength, long dst, long dstCapacity) {
        if (this.closed) {
            throw new IllegalStateException("Deflater is closed");
        }
        return NativeZlib.deflate(this.handle, src, srcLength, dst, dstCapacity);
    }

    @Override
    public void close() {
        this.closed = true;
        this.cleanable.clean();
    }
}
