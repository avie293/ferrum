package cc.avie.ferrum.natives;

/**
 * A native inflate stream for zlib streams, reused across calls.
 *
 * <p>Instances are not thread-safe. The native state is freed once the instance becomes unreachable.
 */
public final class ZlibInflater {
    private final long handle;

    public ZlibInflater() {
        long handle = NativeZlib.newInflater();
        this.handle = handle;
        NativeCleaner.CLEANER.register(this, () -> NativeZlib.freeInflater(handle));
    }

    /**
     * Decompresses the zlib stream at {@code src} into at most {@code dstCapacity} bytes at {@code dst}.
     *
     * @return the number of bytes written, or {@link NativeZlib#ERR_DATA} if the stream is malformed
     */
    public long inflate(long src, long srcLength, long dst, long dstCapacity) {
        return NativeZlib.inflate(this.handle, src, srcLength, dst, dstCapacity);
    }

    /**
     * Decompresses a zlib stream of unknown size; see {@link NativeZlib#inflateStream}.
     */
    public long inflateStream(long src, long srcLength, long dst, long dstCapacity, boolean reset) {
        return NativeZlib.inflateStream(this.handle, src, srcLength, dst, dstCapacity, reset);
    }
}
