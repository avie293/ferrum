package cc.avie.ferrum.network;

import cc.avie.ferrum.natives.NativeZlib;
import cc.avie.ferrum.natives.ZlibDeflater;
import cc.avie.ferrum.natives.ZlibInflater;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.DecoderException;
import io.netty.handler.codec.EncoderException;
import java.lang.foreign.MemorySegment;
import java.util.List;
import java.util.zip.DataFormatException;
import net.minecraft.network.CompressionDecoder;
import net.minecraft.network.VarInt;

/**
 * Packet compression with the native zlib, used by the compression handler mixins.
 *
 * <p>Unlike vanilla, packets are compressed straight from and into Netty's direct buffers instead of being copied
 * through temporary heap arrays. Buffers that cannot be addressed directly are copied into a pooled direct buffer
 * first, so every buffer type keeps working.
 */
public final class NativeCompression {
    private NativeCompression() {
    }

    /**
     * Allocates an output buffer that is large enough for the compressed packet, so it never has to grow.
     */
    public static ByteBuf allocateOutput(ChannelHandlerContext ctx, ByteBuf uncompressed, int threshold) {
        int length = uncompressed.readableBytes();
        if (length > CompressionDecoder.MAXIMUM_UNCOMPRESSED_LENGTH) {
            // Rejected by encode() anyway
            return ctx.alloc().ioBuffer();
        }
        if (length < threshold) {
            return ctx.alloc().ioBuffer(1 + length);
        }
        return ctx.alloc().directBuffer(VarInt.MAX_VARINT_SIZE + (int) NativeZlib.compressBound(length));
    }

    /**
     * [VanillaCopy] {@code CompressionEncoder#encode}
     */
    public static void encode(ZlibDeflater deflater, int threshold, ChannelHandlerContext ctx, ByteBuf uncompressed,
            ByteBuf out) {
        int length = uncompressed.readableBytes();
        if (length > CompressionDecoder.MAXIMUM_UNCOMPRESSED_LENGTH) {
            throw new IllegalArgumentException("Packet too big (is " + length + ", should be less than 8388608)");
        }

        if (length < threshold) {
            VarInt.write(out, 0);
            out.writeBytes(uncompressed);
            return;
        }

        VarInt.write(out, length);
        int bound = (int) NativeZlib.compressBound(length);
        out.ensureWritable(bound);

        ByteBuf src = isAddressable(uncompressed) ? uncompressed : copyToDirect(ctx, uncompressed, length);
        ByteBuf dst = isAddressable(out) ? out : ctx.alloc().directBuffer(bound);
        try {
            long written = deflater.deflate(address(src, src.readerIndex(), length), length,
                    address(dst, dst.writerIndex(), bound), bound);
            if (written < 0) {
                throw new EncoderException("Failed to compress packet of " + length + " bytes");
            }

            dst.writerIndex(dst.writerIndex() + (int) written);
            if (dst != out) {
                out.writeBytes(dst);
            }
            uncompressed.skipBytes(length);
        } finally {
            if (src != uncompressed) {
                src.release();
            }
            if (dst != out) {
                dst.release();
            }
        }
    }

    /**
     * [VanillaCopy] {@code CompressionDecoder#decode}
     */
    public static void decode(ZlibInflater inflater, int threshold, boolean validateDecompressed,
            ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws DataFormatException {
        int uncompressedLength = VarInt.read(in);
        if (uncompressedLength == 0) {
            out.add(in.readBytes(in.readableBytes()));
            return;
        }

        if (validateDecompressed) {
            if (uncompressedLength < threshold) {
                throw new DecoderException("Badly compressed packet - size of " + uncompressedLength
                        + " is below server threshold of " + threshold);
            }
            if (uncompressedLength > CompressionDecoder.MAXIMUM_UNCOMPRESSED_LENGTH) {
                throw new DecoderException("Badly compressed packet - size of " + uncompressedLength
                        + " is larger than protocol maximum of 8388608");
            }
        }

        int compressedLength = in.readableBytes();
        ByteBuf src = isAddressable(in) ? in : copyToDirect(ctx, in, compressedLength);
        ByteBuf output = ctx.alloc().directBuffer(uncompressedLength);
        try {
            long written = inflater.inflate(address(src, src.readerIndex(), compressedLength), compressedLength,
                    address(output, 0, uncompressedLength), uncompressedLength);
            if (written < 0) {
                throw new DataFormatException("Malformed compressed packet");
            }
            if (written != uncompressedLength) {
                throw new DecoderException("Badly compressed packet - actual length of uncompressed payload "
                        + written + " is does not match declared size " + uncompressedLength);
            }

            output.writerIndex(uncompressedLength);
            in.skipBytes(compressedLength);
            out.add(output);
        } catch (Throwable t) {
            output.release();
            throw t;
        } finally {
            if (src != in) {
                src.release();
            }
        }
    }

    private static boolean isAddressable(ByteBuf buf) {
        return buf.hasMemoryAddress() || buf.isDirect() && buf.nioBufferCount() == 1;
    }

    private static long address(ByteBuf buf, int index, int length) {
        if (buf.hasMemoryAddress()) {
            return buf.memoryAddress() + index;
        }
        // Netty only exposes raw addresses when it may use Unsafe, which is not the default on recent JDKs
        return MemorySegment.ofBuffer(buf.internalNioBuffer(index, length)).address();
    }

    private static ByteBuf copyToDirect(ChannelHandlerContext ctx, ByteBuf buf, int length) {
        ByteBuf copy = ctx.alloc().directBuffer(length);
        copy.writeBytes(buf, buf.readerIndex(), length);
        return copy;
    }
}
