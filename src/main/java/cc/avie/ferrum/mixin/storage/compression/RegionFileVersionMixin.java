package cc.avie.ferrum.mixin.storage.compression;

import cc.avie.ferrum.storage.RegionCompression;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import net.minecraft.world.level.chunk.storage.RegionFileVersion;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Handles the default {@code deflate} region compression natively; all other formats stay untouched.
 */
@Mixin(RegionFileVersion.class)
abstract class RegionFileVersionMixin {
    @Inject(method = "wrap(Ljava/io/InputStream;)Ljava/io/InputStream;", at = @At("HEAD"), cancellable = true)
    private void ferrum$wrapInput(InputStream in, CallbackInfoReturnable<InputStream> cir) throws IOException {
        if ((Object) this == RegionFileVersion.VERSION_DEFLATE) {
            cir.setReturnValue(RegionCompression.inflate(in));
        }
    }

    @Inject(method = "wrap(Ljava/io/OutputStream;)Ljava/io/OutputStream;", at = @At("HEAD"), cancellable = true)
    private void ferrum$wrapOutput(OutputStream out, CallbackInfoReturnable<OutputStream> cir) {
        if ((Object) this == RegionFileVersion.VERSION_DEFLATE) {
            cir.setReturnValue(RegionCompression.deflating(out));
        }
    }
}
