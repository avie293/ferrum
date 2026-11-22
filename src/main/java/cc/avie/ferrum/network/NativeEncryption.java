package cc.avie.ferrum.network;

import cc.avie.ferrum.natives.Cfb8Cipher;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import java.security.Key;
import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;
import javax.crypto.Cipher;
import org.jspecify.annotations.Nullable;

/**
 * Connection encryption with the native AES/CFB8, used by the cipher handler mixins.
 *
 * <p>Vanilla only hands initialized JDK {@link Cipher}s to the handlers, which do not reveal their key. The key of
 * every cipher created by {@code Crypt.getCipher} is therefore remembered until a handler picks it up. Ciphers from
 * anywhere else are not known here and keep using the JDK implementation.
 */
public final class NativeEncryption {
    private static final Map<Cipher, byte[]> KEYS = Collections.synchronizedMap(new WeakHashMap<>());

    private NativeEncryption() {
    }

    /**
     * Remembers the key of a freshly initialized cipher. Minecraft uses the key as IV as well.
     */
    public static void remember(Cipher cipher, Key key) {
        byte[] encoded = key.getEncoded();
        if (encoded != null && encoded.length == 16) {
            KEYS.put(cipher, encoded);
        }
    }

    /**
     * Creates the native equivalent of a cipher from {@code Crypt.getCipher} that has not processed any data yet.
     *
     * @return the native cipher, or {@code null} if the cipher was not created by {@code Crypt.getCipher}
     */
    public static @Nullable Cfb8Cipher take(Cipher cipher, boolean encrypt) {
        byte[] key = KEYS.remove(cipher);
        if (key == null) {
            return null;
        }
        try {
            return new Cfb8Cipher(key, key, encrypt);
        } finally {
            Arrays.fill(key, (byte) 0);
        }
    }

    public static ByteBuf allocateOutput(ChannelHandlerContext ctx, ByteBuf msg) {
        return ctx.alloc().ioBuffer(msg.readableBytes());
    }

    /**
     * Writes {@code msg} encrypted to {@code out}.
     */
    public static void encrypt(Cfb8Cipher cipher, ChannelHandlerContext ctx, ByteBuf msg, ByteBuf out) {
        int length = msg.readableBytes();
        int start = out.writerIndex();
        out.writeBytes(msg);

        if (DirectBuffers.isAddressable(out)) {
            cipher.update(DirectBuffers.address(out, start, length), length);
            return;
        }

        ByteBuf temp = ctx.alloc().directBuffer(length);
        try {
            temp.writeBytes(out, start, length);
            cipher.update(DirectBuffers.address(temp, 0, length), length);
            out.setBytes(start, temp, 0, length);
        } finally {
            temp.release();
        }
    }

    /**
     * Decrypts all readable bytes of {@code in}, in place where possible.
     *
     * @return the decrypted bytes, owned by the caller
     */
    public static ByteBuf decrypt(Cfb8Cipher cipher, ChannelHandlerContext ctx, ByteBuf in) {
        int length = in.readableBytes();
        if (DirectBuffers.isAddressable(in) && !in.isReadOnly()) {
            cipher.update(DirectBuffers.address(in, in.readerIndex(), length), length);
            return in.readRetainedSlice(length);
        }

        ByteBuf copy = DirectBuffers.copyToDirect(ctx, in, length);
        in.skipBytes(length);
        cipher.update(DirectBuffers.address(copy, 0, length), length);
        return copy;
    }
}
