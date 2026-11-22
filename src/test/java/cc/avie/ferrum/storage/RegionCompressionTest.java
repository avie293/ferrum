package cc.avie.ferrum.storage;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.Random;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.InflaterInputStream;
import java.util.zip.ZipException;
import net.minecraft.world.level.chunk.storage.RegionFileVersion;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class RegionCompressionTest {
    private static final RegionFileVersion DEFLATE = RegionFileVersion.VERSION_DEFLATE;

    @Test
    void deflateIsHandledNatively() throws IOException {
        assertInstanceOf(DeflatingOutputStream.class, DEFLATE.wrap(new ByteArrayOutputStream()));
    }

    // 65536 fills the initial output buffer exactly, larger sizes make it grow one or more times
    @ParameterizedTest
    @ValueSource(ints = {0, 1, 1000, 65535, 65536, 65537, 300_000, 6_000_000})
    void roundTrip(int length) throws IOException {
        for (byte[] payload : new byte[][]{payload(length), new byte[length]}) {
            assertArrayEquals(payload, read(DEFLATE.wrap(new ByteArrayInputStream(write(payload)))));
        }
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1000, 65536, 300_000})
    void outputIsReadableByVanilla(int length) throws IOException {
        byte[] payload = payload(length);
        assertArrayEquals(payload, read(new InflaterInputStream(new ByteArrayInputStream(write(payload)))));
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1000, 65536, 300_000})
    void readsVanillaOutput(int length) throws IOException {
        byte[] payload = payload(length);
        ByteArrayOutputStream compressed = new ByteArrayOutputStream();
        try (OutputStream out = new DeflaterOutputStream(compressed)) {
            out.write(payload);
        }
        assertArrayEquals(payload, read(DEFLATE.wrap(new ByteArrayInputStream(compressed.toByteArray()))));
    }

    @Test
    void rejectsTruncatedData() throws IOException {
        byte[] compressed = write(payload(300_000));
        byte[] truncated = Arrays.copyOf(compressed, compressed.length / 2);
        assertThrows(ZipException.class, () -> DEFLATE.wrap(new ByteArrayInputStream(truncated)));
    }

    @Test
    void rejectsMalformedData() {
        byte[] garbage = payload(1000);
        assertThrows(ZipException.class, () -> DEFLATE.wrap(new ByteArrayInputStream(garbage)));
    }

    @Test
    void writesAfterCloseFail() throws IOException {
        OutputStream out = DEFLATE.wrap(new ByteArrayOutputStream());
        out.close();
        assertThrows(IOException.class, () -> out.write(1));
    }

    private static byte[] write(byte[] payload) throws IOException {
        ByteArrayOutputStream compressed = new ByteArrayOutputStream();
        try (OutputStream out = DEFLATE.wrap(compressed)) {
            // Mix single bytes and blocks like NBT serialization does
            int half = payload.length / 2;
            for (int i = 0; i < half; i++) {
                out.write(payload[i]);
            }
            out.write(payload, half, payload.length - half);
        }
        return compressed.toByteArray();
    }

    private static byte[] read(InputStream in) throws IOException {
        try (in) {
            return in.readAllBytes();
        }
    }

    private static byte[] payload(int length) {
        Random random = new Random(length);
        byte[] bytes = new byte[length];
        for (int i = 0; i < length; i++) {
            bytes[i] = (byte) (random.nextInt(8) == 0 ? random.nextInt() : i >> 6);
        }
        return bytes;
    }
}
