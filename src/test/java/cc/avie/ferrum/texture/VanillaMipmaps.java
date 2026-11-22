package cc.avie.ferrum.texture;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.util.ARGB;

/**
 * [VanillaCopy] The alpha coverage functions of {@code MipmapGenerator} in Minecraft 26.3, used as the reference for
 * the native implementation.
 */
final class VanillaMipmaps {
    private VanillaMipmaps() {
    }

    static float alphaTestCoverage(NativeImage image, float alphaRef, float alphaScale) {
        int width = image.getWidth();
        int height = image.getHeight();
        float coverage = 0.0F;

        for (int y = 0; y < height - 1; y++) {
            for (int x = 0; x < width - 1; x++) {
                float alpha00 = Math.clamp(ARGB.alphaFloat(image.getPixel(x, y)) * alphaScale, 0.0F, 1.0F);
                float alpha10 = Math.clamp(ARGB.alphaFloat(image.getPixel(x + 1, y)) * alphaScale, 0.0F, 1.0F);
                float alpha01 = Math.clamp(ARGB.alphaFloat(image.getPixel(x, y + 1)) * alphaScale, 0.0F, 1.0F);
                float alpha11 = Math.clamp(ARGB.alphaFloat(image.getPixel(x + 1, y + 1)) * alphaScale, 0.0F, 1.0F);
                float texelCoverage = 0.0F;

                for (int subsampleY = 0; subsampleY < 4; subsampleY++) {
                    float fy = (subsampleY + 0.5F) / 4.0F;
                    for (int subsampleX = 0; subsampleX < 4; subsampleX++) {
                        float fx = (subsampleX + 0.5F) / 4.0F;
                        float alpha = alpha00 * (1.0F - fx) * (1.0F - fy) + alpha10 * fx * (1.0F - fy)
                                + alpha01 * (1.0F - fx) * fy + alpha11 * fx * fy;
                        if (alpha > alphaRef) {
                            texelCoverage++;
                        }
                    }
                }

                coverage += texelCoverage / 16.0F;
            }
        }

        return coverage / ((width - 1) * (height - 1));
    }

    static void scaleAlphaToCoverage(NativeImage image, float desiredCoverage, float alphaRef,
            float alphaCutoffBias) {
        float minAlphaScale = 0.0F;
        float maxAlphaScale = 4.0F;
        float alphaScale = 1.0F;
        float bestAlphaScale = 1.0F;
        float bestError = Float.MAX_VALUE;

        for (int i = 0; i < 5; i++) {
            float currentCoverage = alphaTestCoverage(image, alphaRef, alphaScale);
            float error = Math.abs(currentCoverage - desiredCoverage);
            if (error < bestError) {
                bestError = error;
                bestAlphaScale = alphaScale;
            }

            if (currentCoverage < desiredCoverage) {
                minAlphaScale = alphaScale;
            } else {
                if (!(currentCoverage > desiredCoverage)) {
                    break;
                }
                maxAlphaScale = alphaScale;
            }

            alphaScale = (minAlphaScale + maxAlphaScale) * 0.5F;
        }

        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                int pixel = image.getPixel(x, y);
                float alpha = ARGB.alphaFloat(pixel);
                alpha = alpha * bestAlphaScale + alphaCutoffBias + 0.025F;
                alpha = Math.clamp(alpha, 0.0F, 1.0F);
                image.setPixel(x, y, ARGB.color(alpha, pixel));
            }
        }
    }
}
