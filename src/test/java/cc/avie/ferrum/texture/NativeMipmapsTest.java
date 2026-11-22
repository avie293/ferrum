package cc.avie.ferrum.texture;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mojang.blaze3d.platform.NativeImage;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import net.minecraft.client.renderer.texture.MipmapGenerator;
import net.minecraft.util.ARGB;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class NativeMipmapsTest {
    private static final String[] TEXTURES = {
            "oak_leaves", "short_grass", "glass", "oak_sapling", "torch", "rail", "cobweb", "vine", "dandelion",
            "iron_bars", "ladder", "kelp", "sugar_cane", "wheat_stage7", "fern", "spawner", "oak_door_top"
    };

    @Test
    void mixinIsApplied() {
        assertTrue(Arrays.stream(MipmapGenerator.class.getDeclaredMethods())
                .anyMatch(method -> method.getName().contains("ferrum$alphaTestCoverage")));
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 2, 8})
    void matchesVanilla(int upscale) throws Exception {
        Method coverage = privateMethod("alphaTestCoverage", NativeImage.class, float.class, float.class);
        Method scale = privateMethod("scaleAlphaToCoverage", NativeImage.class, float.class, float.class,
                float.class);
        List<String> checked = new ArrayList<>();

        for (String name : TEXTURES) {
            try (NativeImage image = load(name, upscale)) {
                for (float alphaScale : new float[]{1.0F, 0.5F, 1.75F, 3.0F}) {
                    float expected = VanillaMipmaps.alphaTestCoverage(image, 0.5F, alphaScale);
                    float actual = (float) coverage.invoke(null, image, 0.5F, alphaScale);
                    assertEquals(Float.floatToRawIntBits(expected), Float.floatToRawIntBits(actual), name);
                }

                // Shrink to the first mip level, then let both implementations rescale its alpha
                float desired = VanillaMipmaps.alphaTestCoverage(image, 0.3F, 1.0F);
                try (NativeImage vanilla = half(image); NativeImage ferrum = half(image)) {
                    VanillaMipmaps.scaleAlphaToCoverage(vanilla, desired, 0.3F, 0.1F);
                    scale.invoke(null, ferrum, desired, 0.3F, 0.1F);
                    assertArrayEquals(vanilla.getPixelsABGR(), ferrum.getPixelsABGR(), name);
                }
                checked.add(name);
            }
        }
        assertFalse(checked.isEmpty());
    }

    private static Method privateMethod(String name, Class<?>... parameters) throws NoSuchMethodException {
        Method method = MipmapGenerator.class.getDeclaredMethod(name, parameters);
        method.setAccessible(true);
        return method;
    }

    private static NativeImage load(String name, int upscale) throws IOException {
        String path = "/assets/minecraft/textures/block/" + name + ".png";
        try (InputStream in = NativeMipmapsTest.class.getResourceAsStream(path)) {
            if (in == null) {
                throw new IOException("Missing texture " + path);
            }
            NativeImage source = NativeImage.read(in);
            if (upscale == 1) {
                return source;
            }
            try (source) {
                NativeImage scaled = new NativeImage(source.getWidth() * upscale, source.getHeight() * upscale, false);
                for (int y = 0; y < scaled.getHeight(); y++) {
                    for (int x = 0; x < scaled.getWidth(); x++) {
                        scaled.setPixel(x, y, source.getPixel(x / upscale, y / upscale));
                    }
                }
                return scaled;
            }
        }
    }

    private static NativeImage half(NativeImage image) {
        NativeImage result = new NativeImage(image.getWidth() >> 1, image.getHeight() >> 1, false);
        for (int y = 0; y < result.getHeight(); y++) {
            for (int x = 0; x < result.getWidth(); x++) {
                result.setPixel(x, y, ARGB.meanLinear(image.getPixel(x * 2, y * 2), image.getPixel(x * 2 + 1, y * 2),
                        image.getPixel(x * 2, y * 2 + 1), image.getPixel(x * 2 + 1, y * 2 + 1)));
            }
        }
        return result;
    }
}
