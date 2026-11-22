package cc.avie.ferrum.storage;

import cc.avie.ferrum.natives.NativeZlib;
import cc.avie.ferrum.natives.ZlibDeflater;
import cc.avie.ferrum.natives.ZlibInflater;
import it.unimi.dsi.fastutil.io.FastByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.util.zip.Deflater;
import java.util.zip.GZIPInputStream;
import java.util.zip.ZipException;
import org.jspecify.annotations.Nullable;

/**
 * One-shot zlib and gzip compression of region file chunks and NBT files with the native zlib.
 *
 * <p>Vanilla streams every chunk or file through a new {@link Deflater} or {@link java.util.zip.Inflater},
 * allocating and freeing the native zlib state each time. Here each thread keeps its native streams and buffers, and
 * the data is compressed or decompressed in a single call.
 */
public final class StreamCompression {
    /**
     * Larger buffers are released after use, so a single huge chunk or file does not pin memory for the thread's
     * lifetime.
     */
    private static final long MAX_RETAINED_BUFFER = 1 << 20;
    private static final long MIN_INFLATE_BUFFER = 64 << 10;
    /**
     * Gzip files that decompress to more than this are left to the JDK's streaming implementation, so the size limits
     * vanilla enforces while parsing NBT still apply before the data is fully in memory.
     */
    private static final long MAX_GUNZIP_SIZE = 64 << 20;

    private static final ThreadLocal<Context> CONTEXT = ThreadLocal.withInitial(Context::new);

    private StreamCompression() {
    }

    /**
     * {@return a stream that writes everything written to it as one zlib or gzip stream to {@code out} when closed}
     */
    public static OutputStream deflating(OutputStream out, boolean gzip) {
        return new DeflatingOutputStream(out, gzip);
    }

    /**
     * Compresses {@code length} bytes of {@code data} into a zlib or gzip stream and writes it to {@code out}.
     */
    static void deflate(byte[] data, int length, OutputStream out, boolean gzip) throws IOException {
        Context context = CONTEXT.get();
        try {
            MemorySegment src = context.input(length);
            MemorySegment.copy(data, 0, src, ValueLayout.JAVA_BYTE, 0, length);

            // A gzip stream only adds a fixed size header and trailer to the zlib bound
            MemorySegment dst = context.output(NativeZlib.compressBound(length) + 18);
            ZlibDeflater deflater = gzip ? context.gzipDeflater() : context.deflater;
            long written = deflater.deflate(src.address(), length, dst.address(), dst.byteSize());
            if (written < 0) {
                throw new IOException("Failed to compress " + length + " bytes");
            }
            out.write(dst.asSlice(0, written).toArray(ValueLayout.JAVA_BYTE));
        } finally {
            context.trim();
        }
    }

    /**
     * Reads and decompresses the whole zlib stream from {@code in}, which is closed afterwards.
     */
    public static InputStream inflate(InputStream in) throws IOException {
        byte[] compressed;
        try (in) {
            compressed = in.readAllBytes();
        }

        Context context = CONTEXT.get();
        try {
            MemorySegment src = context.input(compressed.length);
            MemorySegment.copy(compressed, 0, src, ValueLayout.JAVA_BYTE, 0, compressed.length);

            MemorySegment dst = context.output(Math.max(MIN_INFLATE_BUFFER, compressed.length * 4L));
            long produced = 0;
            boolean reset = true;
            while (true) {
                long result = context.inflater.inflateStream(src.address(), compressed.length,
                        dst.address() + produced, dst.byteSize() - produced, reset);
                reset = false;
                if (result >= 0) {
                    produced = result;
                    break;
                }
                if (result != NativeZlib.ERR_BUFFER) {
                    throw new ZipException("Invalid or truncated zlib stream in chunk data");
                }

                produced = dst.byteSize();
                if (produced * 2 > Integer.MAX_VALUE - 8) {
                    throw new ZipException("Chunk data is too large");
                }
                dst = context.growOutput(produced * 2, produced);
            }
            return new FastByteArrayInputStream(dst.asSlice(0, produced).toArray(ValueLayout.JAVA_BYTE));
        } finally {
            context.trim();
        }
    }

    /**
     * Reads and decompresses all gzip data from {@code in}, which is closed afterwards.
     */
    public static InputStream gunzip(InputStream in) throws IOException {
        byte[] compressed;
        try (in) {
            compressed = in.readAllBytes();
        }

        InputStream result = gunzipNative(compressed);
        return result != null ? result : new GZIPInputStream(new FastByteArrayInputStream(compressed));
    }

    private static @Nullable InputStream gunzipNative(byte[] compressed) throws IOException {
        // The trailer of the last member holds its uncompressed size, which is the full size for a single member
        long size = compressed.length >= 18 ? Integer.toUnsignedLong(lastInt(compressed)) : 0;
        if (size > MAX_GUNZIP_SIZE) {
            return null;
        }

        Context context = CONTEXT.get();
        try {
            MemorySegment src = context.input(compressed.length);
            MemorySegment.copy(compressed, 0, src, ValueLayout.JAVA_BYTE, 0, compressed.length);

            long capacity = Math.max(size, MIN_INFLATE_BUFFER);
            while (true) {
                MemorySegment dst = context.output(capacity);
                long result = NativeZlib.gunzip(src.address(), compressed.length, dst.address(), dst.byteSize());
                if (result >= 0) {
                    return new FastByteArrayInputStream(dst.asSlice(0, result).toArray(ValueLayout.JAVA_BYTE));
                }
                if (result != NativeZlib.ERR_BUFFER) {
                    throw new ZipException("Invalid gzip data");
                }

                capacity = dst.byteSize() * 2;
                if (capacity > MAX_GUNZIP_SIZE) {
                    return null;
                }
            }
        } finally {
            context.trim();
        }
    }

    private static int lastInt(byte[] bytes) {
        int end = bytes.length;
        return (bytes[end - 4] & 0xFF) | (bytes[end - 3] & 0xFF) << 8 | (bytes[end - 2] & 0xFF) << 16
                | (bytes[end - 1] & 0xFF) << 24;
    }

    /**
     * Native streams and scratch buffers of one thread. The buffers are managed by the GC, so they are released
     * together with the thread.
     */
    private static final class Context {
        final ZlibDeflater deflater = new ZlibDeflater(Deflater.DEFAULT_COMPRESSION);
        final ZlibInflater inflater = new ZlibInflater();
        private @Nullable ZlibDeflater gzipDeflater;
        private MemorySegment input = MemorySegment.NULL;
        private MemorySegment output = MemorySegment.NULL;

        ZlibDeflater gzipDeflater() {
            ZlibDeflater deflater = this.gzipDeflater;
            if (deflater == null) {
                deflater = ZlibDeflater.gzip(Deflater.DEFAULT_COMPRESSION);
                this.gzipDeflater = deflater;
            }
            return deflater;
        }

        MemorySegment input(long size) {
            if (this.input.byteSize() < size) {
                this.input = Arena.ofAuto().allocate(size);
            }
            return this.input;
        }

        MemorySegment output(long size) {
            if (this.output.byteSize() < size) {
                this.output = Arena.ofAuto().allocate(size);
            }
            return this.output;
        }

        MemorySegment growOutput(long size, long keep) {
            MemorySegment grown = Arena.ofAuto().allocate(size);
            MemorySegment.copy(this.output, 0, grown, 0, keep);
            this.output = grown;
            return grown;
        }

        void trim() {
            if (this.input.byteSize() > MAX_RETAINED_BUFFER) {
                this.input = MemorySegment.NULL;
            }
            if (this.output.byteSize() > MAX_RETAINED_BUFFER) {
                this.output = MemorySegment.NULL;
            }
        }
    }
}
