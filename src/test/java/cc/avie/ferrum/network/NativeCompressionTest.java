package cc.avie.ferrum.network;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.DecoderException;
import java.io.ByteArrayOutputStream;
import java.lang.reflect.Field;
import java.util.Random;
import java.util.zip.DataFormatException;
import java.util.zip.Deflater;
import java.util.zip.Inflater;
import net.minecraft.network.CompressionDecoder;
import net.minecraft.network.CompressionEncoder;
import net.minecraft.network.VarInt;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class NativeCompressionTest {
    private static final int THRESHOLD = 256;

    @Test
    void jdkStreamsAreNotAllocated() throws ReflectiveOperationException {
        assertNull(field(CompressionEncoder.class, "deflater").get(new CompressionEncoder(THRESHOLD)));
        assertNull(field(CompressionDecoder.class, "inflater").get(new CompressionDecoder(THRESHOLD, true)));
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 255, 256, 4096, 100_000, 2_000_000, 8_388_608})
    void roundTrip(int length) {
        byte[] payload = payload(length);
        for (boolean direct : new boolean[]{true, false}) {
            ByteBuf encoded = encode(payload, direct);
            assertArrayEquals(payload, decode(encoded));
        }
    }

    @ParameterizedTest
    @ValueSource(ints = {256, 4096, 100_000, 2_000_000})
    void outputIsReadableByVanilla(int length) throws DataFormatException {
        byte[] payload = payload(length);
        ByteBuf encoded = encode(payload, true);
        assertEquals(length, VarInt.read(encoded));

        Inflater inflater = new Inflater();
        inflater.setInput(ByteBufUtil.getBytes(encoded));
        byte[] result = new byte[length];
        assertEquals(length, inflater.inflate(result));
        assertArrayEquals(payload, result);
        encoded.release();
    }

    @ParameterizedTest
    @ValueSource(ints = {256, 4096, 100_000, 2_000_000})
    void readsVanillaOutput(int length) {
        byte[] payload = payload(length);
        assertArrayEquals(payload, decode(vanillaPacket(payload, payload.length)));
    }

    @Test
    void rejectsMismatchedLength() {
        byte[] payload = payload(4096);
        assertThrows(DecoderException.class, () -> decode(vanillaPacket(payload, payload.length + 1)));
    }

    @Test
    void rejectsMalformedData() {
        ByteBuf packet = Unpooled.directBuffer();
        VarInt.write(packet, 4096);
        packet.writeBytes(payload(1000));
        DecoderException e = assertThrows(DecoderException.class, () -> decode(packet));
        assertInstanceOf(DataFormatException.class, e.getCause());
    }

    @Test
    void rejectsTooLargePacket() {
        EmbeddedChannel channel = new EmbeddedChannel(new CompressionEncoder(THRESHOLD));
        assertThrows(Exception.class,
                () -> channel.writeOutbound(Unpooled.wrappedBuffer(new byte[8_388_609])));
        channel.finishAndReleaseAll();
    }

    private static ByteBuf encode(byte[] payload, boolean direct) {
        EmbeddedChannel channel = new EmbeddedChannel(new CompressionEncoder(THRESHOLD));
        ByteBuf in = direct ? Unpooled.directBuffer(payload.length) : Unpooled.buffer(payload.length);
        channel.writeOutbound(in.writeBytes(payload));
        ByteBuf out = channel.readOutbound();
        channel.finish();
        return out;
    }

    private static byte[] decode(ByteBuf packet) {
        EmbeddedChannel channel = new EmbeddedChannel(new CompressionDecoder(THRESHOLD, true));
        try {
            channel.writeInbound(packet);
            ByteBuf out = channel.readInbound();
            byte[] bytes = ByteBufUtil.getBytes(out);
            out.release();
            return bytes;
        } finally {
            channel.finishAndReleaseAll();
        }
    }

    private static ByteBuf vanillaPacket(byte[] payload, int declaredLength) {
        Deflater deflater = new Deflater();
        deflater.setInput(payload);
        deflater.finish();
        ByteArrayOutputStream compressed = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        while (!deflater.finished()) {
            compressed.write(buffer, 0, deflater.deflate(buffer));
        }
        deflater.end();

        ByteBuf packet = Unpooled.buffer();
        VarInt.write(packet, declaredLength);
        return packet.writeBytes(compressed.toByteArray());
    }

    /**
     * Mix of repetitive and random bytes, roughly like chunk data.
     */
    private static byte[] payload(int length) {
        Random random = new Random(length);
        byte[] bytes = new byte[length];
        for (int i = 0; i < length; i++) {
            bytes[i] = (byte) (random.nextInt(8) == 0 ? random.nextInt() : i >> 6);
        }
        return bytes;
    }

    private static Field field(Class<?> owner, String name) throws NoSuchFieldException {
        Field field = owner.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }
}
