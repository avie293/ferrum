package cc.avie.ferrum.network;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import java.lang.foreign.MemorySegment;

/**
 * Access to the native memory of Netty buffers, so they can be handed to native code without copying.
 */
final class DirectBuffers {
    private DirectBuffers() {
    }

    static boolean isAddressable(ByteBuf buf) {
        return buf.hasMemoryAddress() || buf.isDirect() && buf.nioBufferCount() == 1;
    }

    /**
     * {@return the address of {@code length} bytes at {@code index}}, for a buffer that {@linkplain #isAddressable is
     * addressable}
     */
    static long address(ByteBuf buf, int index, int length) {
        if (buf.hasMemoryAddress()) {
            return buf.memoryAddress() + index;
        }
        // Netty only exposes raw addresses when it may use Unsafe, which is not the default on recent JDKs
        return MemorySegment.ofBuffer(buf.internalNioBuffer(index, length)).address();
    }

    /**
     * Copies {@code length} readable bytes of {@code buf} into a new direct buffer without changing its reader index.
     */
    static ByteBuf copyToDirect(ChannelHandlerContext ctx, ByteBuf buf, int length) {
        ByteBuf copy = ctx.alloc().directBuffer(length);
        copy.writeBytes(buf, buf.readerIndex(), length);
        return copy;
    }
}
