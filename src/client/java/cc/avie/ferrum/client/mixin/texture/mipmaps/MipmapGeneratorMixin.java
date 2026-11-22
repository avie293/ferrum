package cc.avie.ferrum.client.mixin.texture.mipmaps;

import cc.avie.ferrum.natives.NativeMipmaps;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.renderer.texture.MipmapGenerator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Runs the alpha coverage search of cutout mipmaps natively, with identical results.
 */
@Mixin(MipmapGenerator.class)
abstract class MipmapGeneratorMixin {
    @Inject(method = "alphaTestCoverage", at = @At("HEAD"), cancellable = true)
    private static void ferrum$alphaTestCoverage(NativeImage image, float alphaRef, float alphaScale,
            CallbackInfoReturnable<Float> cir) {
        if (image.format() == NativeImage.Format.RGBA) {
            cir.setReturnValue(NativeMipmaps.alphaTestCoverage(image.getPointer(), image.getWidth(),
                    image.getHeight(), alphaRef, alphaScale));
        }
    }

    @Inject(method = "scaleAlphaToCoverage", at = @At("HEAD"), cancellable = true)
    private static void ferrum$scaleAlphaToCoverage(NativeImage image, float desiredCoverage, float alphaRef,
            float alphaCutoffBias, CallbackInfo ci) {
        if (image.format() == NativeImage.Format.RGBA) {
            NativeMipmaps.scaleAlphaToCoverage(image.getPointer(), image.getWidth(), image.getHeight(),
                    desiredCoverage, alphaRef, alphaCutoffBias);
            ci.cancel();
        }
    }
}
