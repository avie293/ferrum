package cc.avie.ferrum.natives;

/**
 * A native deflate stream producing complete zlib or gzip streams, reused across calls.
 *
 * <p>Instances are not thread-safe. The native state is freed once the instance becomes unreachable.
 */
public final class ZlibDeflater {
    private final long handle;

    /**
     * @param level the zlib compression level, {@code -1} for the default level
     */
    public ZlibDeflater(int level) {
        this(NativeZlib.newDeflater(level));
    }

    private ZlibDeflater(long handle) {
        this.handle = handle;
        NativeCleaner.CLEANER.register(this, () -> NativeZlib.freeDeflater(handle));
    }

    /**
     * Creates a deflater that produces gzip instead of zlib streams.
     *
     * @param level the zlib compression level, {@code -1} for the default level
     */
    public static ZlibDeflater gzip(int level) {
        return new ZlibDeflater(NativeZlib.newGzipDeflater(level));
    }

    /**
     * Compresses {@code srcLength} bytes at {@code src} into {@code dst}.
     *
     * @return the length of the compressed stream, or {@link NativeZlib#ERR_BUFFER} if it does not fit
     */
    public long deflate(long src, long srcLength, long dst, long dstCapacity) {
        return NativeZlib.deflate(this.handle, src, srcLength, dst, dstCapacity);
    }
}
