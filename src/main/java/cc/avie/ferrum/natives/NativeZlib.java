package cc.avie.ferrum.natives;

import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.lang.ref.Cleaner;

/**
 * Bindings for the native zlib routines.
 *
 * <p>Buffers are passed as raw addresses and must stay valid and unmoved for the duration of a call. Streams are
 * not thread-safe; each one must only be used by one thread at a time.
 */
public final class NativeZlib {
    /**
     * Returned by {@link #inflate} when the input is not a valid zlib stream.
     */
    public static final long ERR_DATA = -1;
    /**
     * Returned by {@link #deflate} when the output buffer is too small.
     */
    public static final long ERR_BUFFER = -2;

    /**
     * Frees streams that were never closed explicitly, for example when a connection is dropped before its
     * handlers are removed.
     */
    static final Cleaner CLEANER = Cleaner.create();

    private static final MethodHandle COMPRESS_BOUND = NativeLibrary.bind("ferrum_zlib_compress_bound",
            FunctionDescriptor.of(ValueLayout.JAVA_LONG, ValueLayout.JAVA_LONG));
    private static final MethodHandle DEFLATER_NEW = NativeLibrary.bind("ferrum_deflater_new",
            FunctionDescriptor.of(ValueLayout.JAVA_LONG, ValueLayout.JAVA_INT));
    private static final MethodHandle DEFLATER_FREE = NativeLibrary.bind("ferrum_deflater_free",
            FunctionDescriptor.ofVoid(ValueLayout.JAVA_LONG));
    private static final MethodHandle DEFLATE = NativeLibrary.bind("ferrum_deflate", transferDescriptor());
    private static final MethodHandle INFLATER_NEW = NativeLibrary.bind("ferrum_inflater_new",
            FunctionDescriptor.of(ValueLayout.JAVA_LONG));
    private static final MethodHandle INFLATER_FREE = NativeLibrary.bind("ferrum_inflater_free",
            FunctionDescriptor.ofVoid(ValueLayout.JAVA_LONG));
    private static final MethodHandle INFLATE = NativeLibrary.bind("ferrum_inflate", transferDescriptor());

    private NativeZlib() {
    }

    private static FunctionDescriptor transferDescriptor() {
        return FunctionDescriptor.of(ValueLayout.JAVA_LONG, ValueLayout.JAVA_LONG, ValueLayout.JAVA_LONG,
                ValueLayout.JAVA_LONG, ValueLayout.JAVA_LONG, ValueLayout.JAVA_LONG);
    }

    /**
     * {@return the maximum size of a zlib stream holding {@code length} bytes}
     */
    public static long compressBound(long length) {
        try {
            return (long) COMPRESS_BOUND.invokeExact(length);
        } catch (Throwable t) {
            throw new IllegalStateException(t);
        }
    }

    /**
     * Creates a deflate stream that must be released with {@link #freeDeflater}.
     *
     * @param level the zlib compression level, {@code -1} for the default level
     */
    public static long newDeflater(int level) {
        try {
            return (long) DEFLATER_NEW.invokeExact(level);
        } catch (Throwable t) {
            throw new IllegalStateException(t);
        }
    }

    public static void freeDeflater(long deflater) {
        try {
            DEFLATER_FREE.invokeExact(deflater);
        } catch (Throwable t) {
            throw new IllegalStateException(t);
        }
    }

    /**
     * Compresses {@code srcLength} bytes at {@code src} into a complete zlib stream at {@code dst}.
     *
     * @return the length of the compressed stream, or {@link #ERR_BUFFER} if it does not fit into {@code dstCapacity}
     */
    public static long deflate(long deflater, long src, long srcLength, long dst, long dstCapacity) {
        try {
            return (long) DEFLATE.invokeExact(deflater, src, srcLength, dst, dstCapacity);
        } catch (Throwable t) {
            throw new IllegalStateException(t);
        }
    }

    /**
     * Creates an inflate stream that must be released with {@link #freeInflater}.
     */
    public static long newInflater() {
        try {
            return (long) INFLATER_NEW.invokeExact();
        } catch (Throwable t) {
            throw new IllegalStateException(t);
        }
    }

    public static void freeInflater(long inflater) {
        try {
            INFLATER_FREE.invokeExact(inflater);
        } catch (Throwable t) {
            throw new IllegalStateException(t);
        }
    }

    /**
     * Decompresses the zlib stream at {@code src} into at most {@code dstCapacity} bytes at {@code dst}.
     *
     * <p>Like {@link java.util.zip.Inflater}, decompression stops once the output is full, so callers have to compare
     * the result with the length they expect.
     *
     * @return the number of bytes written, or {@link #ERR_DATA} if the stream is malformed
     */
    public static long inflate(long inflater, long src, long srcLength, long dst, long dstCapacity) {
        try {
            return (long) INFLATE.invokeExact(inflater, src, srcLength, dst, dstCapacity);
        } catch (Throwable t) {
            throw new IllegalStateException(t);
        }
    }
}
