package cc.avie.ferrum.network;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import java.lang.reflect.Field;
import java.util.Random;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import net.minecraft.network.CipherDecoder;
import net.minecraft.network.CipherEncoder;
import net.minecraft.util.Crypt;
import org.junit.jupiter.api.Test;

class NativeEncryptionTest {
    private static final int[] SIZES = {1, 15, 16, 17, 63, 64, 65, 200, 1500, 70_000, 3, 0, 128, 2_000_000, 7};

    @Test
    void ciphersFromCryptAreNative() throws Exception {
        SecretKey key = key(1);
        assertNotNull(nativeCipher(new CipherEncoder(Crypt.getCipher(Cipher.ENCRYPT_MODE, key))));
        assertNotNull(nativeCipher(new CipherDecoder(Crypt.getCipher(Cipher.DECRYPT_MODE, key))));
    }

    @Test
    void otherCiphersStayVanilla() throws Exception {
        assertNull(nativeCipher(new CipherEncoder(jdkCipher(Cipher.ENCRYPT_MODE, key(2)))));
    }

    @Test
    void encryptsLikeTheJdk() throws Exception {
        SecretKey key = key(3);
        Cipher reference = jdkCipher(Cipher.ENCRYPT_MODE, key);
        EmbeddedChannel channel = new EmbeddedChannel(new CipherEncoder(Crypt.getCipher(Cipher.ENCRYPT_MODE, key)));
        Random random = new Random(3);

        for (int i = 0; i < SIZES.length; i++) {
            byte[] plain = bytes(random, SIZES[i]);
            channel.writeOutbound(buffer(plain, i % 2 == 0));
            ByteBuf encrypted = channel.readOutbound();
            assertArrayEquals(update(reference, plain), bytes(encrypted));
        }
        channel.finishAndReleaseAll();
    }

    @Test
    void decryptsLikeTheJdk() throws Exception {
        SecretKey key = key(4);
        Cipher reference = jdkCipher(Cipher.ENCRYPT_MODE, key);
        EmbeddedChannel channel = new EmbeddedChannel(new CipherDecoder(Crypt.getCipher(Cipher.DECRYPT_MODE, key)));
        Random random = new Random(4);

        for (int i = 0; i < SIZES.length; i++) {
            byte[] plain = bytes(random, SIZES[i]);
            if (plain.length == 0) {
                continue;
            }
            channel.writeInbound(buffer(update(reference, plain), i % 2 == 0));
            ByteBuf decrypted = channel.readInbound();
            assertArrayEquals(plain, bytes(decrypted));
        }
        channel.finishAndReleaseAll();
    }

    private static Object nativeCipher(Object handler) throws ReflectiveOperationException {
        Field field = handler.getClass().getDeclaredField("ferrum$cipher");
        field.setAccessible(true);
        return field.get(handler);
    }

    private static SecretKey key(long seed) {
        byte[] key = new byte[16];
        new Random(seed).nextBytes(key);
        return new SecretKeySpec(key, "AES");
    }

    private static Cipher jdkCipher(int mode, SecretKey key) throws Exception {
        Cipher cipher = Cipher.getInstance("AES/CFB8/NoPadding");
        cipher.init(mode, key, new IvParameterSpec(key.getEncoded()));
        return cipher;
    }

    // The JDK returns null instead of an empty array when there is no output
    private static byte[] update(Cipher cipher, byte[] input) {
        byte[] output = cipher.update(input);
        return output == null ? new byte[0] : output;
    }

    private static byte[] bytes(Random random, int length) {
        byte[] bytes = new byte[length];
        random.nextBytes(bytes);
        return bytes;
    }

    private static ByteBuf buffer(byte[] bytes, boolean direct) {
        ByteBuf buf = direct ? Unpooled.directBuffer(bytes.length) : Unpooled.buffer(bytes.length);
        return buf.writeBytes(bytes);
    }

    private static byte[] bytes(ByteBuf buf) {
        byte[] bytes = ByteBufUtil.getBytes(buf);
        buf.release();
        return bytes;
    }
}
