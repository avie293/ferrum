package cc.avie.ferrum.storage;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Random;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.world.level.chunk.storage.RegionFileVersion;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class GzipCompressionTest {
    @ParameterizedTest
    @ValueSource(strings = {"igloo/top", "village/plains/houses/plains_small_house_1", "shipwreck/rightsideup_full",
            "end_city/ship", "bastion/treasure/bases/lava_basin"})
    void readsStructureTemplates(String name) throws IOException {
        byte[] file = resource("/data/minecraft/structure/" + name + ".nbt");
        CompoundTag expected = NbtIo.read(new DataInputStream(new GZIPInputStream(new ByteArrayInputStream(file))));
        assertEquals(expected, NbtIo.readCompressed(new ByteArrayInputStream(file), NbtAccounter.unlimitedHeap()));
    }

    @Test
    void writesFilesTheJdkCanRead() throws IOException {
        byte[] file = resource("/data/minecraft/structure/igloo/top.nbt");
        CompoundTag tag = NbtIo.readCompressed(new ByteArrayInputStream(file), NbtAccounter.unlimitedHeap());

        ByteArrayOutputStream written = new ByteArrayOutputStream();
        NbtIo.writeCompressed(tag, written);
        CompoundTag read = NbtIo.read(new DataInputStream(new GZIPInputStream(
                new ByteArrayInputStream(written.toByteArray()))));
        assertEquals(tag, read);
    }

    @Test
    void readsMultipleMembersLikeTheJdk() throws IOException {
        ByteArrayOutputStream data = new ByteArrayOutputStream();
        data.write(jdkGzip(payload(1000, 1)));
        data.write(jdkGzip(payload(70_000, 2)));
        data.write(jdkGzip(new byte[0]));
        data.write(jdkGzip(payload(5, 3)));
        byte[] gzip = data.toByteArray();

        assertArrayEquals(jdkGunzip(gzip), read(StreamCompression.gunzip(new ByteArrayInputStream(gzip))));
    }

    @Test
    void ignoresTrailingBytesLikeTheJdk() throws IOException {
        byte[] gzip = jdkGzip(payload(4096, 4));
        byte[] padded = new byte[gzip.length + 3];
        System.arraycopy(gzip, 0, padded, 0, gzip.length);

        assertArrayEquals(jdkGunzip(padded), read(StreamCompression.gunzip(new ByteArrayInputStream(padded))));
    }

    @Test
    void rejectsMalformedData() {
        byte[] gzip = jdkGzip(payload(4096, 5));
        gzip[20] ^= 0x55;
        assertThrows(IOException.class, () -> read(StreamCompression.gunzip(new ByteArrayInputStream(gzip))));
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 65536, 300_000})
    void regionGzipRoundTrip(int length) throws IOException {
        byte[] payload = payload(length, length);
        ByteArrayOutputStream compressed = new ByteArrayOutputStream();
        try (OutputStream out = RegionFileVersion.VERSION_GZIP.wrap(compressed)) {
            out.write(payload);
        }

        assertArrayEquals(payload, jdkGunzip(compressed.toByteArray()));
        assertArrayEquals(payload, read(RegionFileVersion.VERSION_GZIP.wrap(
                new ByteArrayInputStream(compressed.toByteArray()))));
    }

    private static byte[] resource(String path) throws IOException {
        try (InputStream in = GzipCompressionTest.class.getResourceAsStream(path)) {
            if (in == null) {
                throw new IOException("Missing resource " + path);
            }
            return in.readAllBytes();
        }
    }

    private static byte[] jdkGzip(byte[] data) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (GZIPOutputStream gzip = new GZIPOutputStream(out)) {
            gzip.write(data);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        return out.toByteArray();
    }

    private static byte[] jdkGunzip(byte[] data) throws IOException {
        return read(new GZIPInputStream(new ByteArrayInputStream(data)));
    }

    private static byte[] read(InputStream in) throws IOException {
        try (in) {
            return in.readAllBytes();
        }
    }

    private static byte[] payload(int length, long seed) {
        Random random = new Random(seed);
        byte[] bytes = new byte[length];
        for (int i = 0; i < length; i++) {
            bytes[i] = (byte) (random.nextInt(8) == 0 ? random.nextInt() : i >> 6);
        }
        return bytes;
    }
}
