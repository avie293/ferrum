package cc.avie.ferrum.mixin.storage.compression;

import cc.avie.ferrum.storage.StreamCompression;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import net.minecraft.world.level.chunk.storage.RegionFileVersion;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Handles the {@code deflate} (default) and gzip region compression natively; all other formats stay untouched.
 */
@Mixin(RegionFileVersion.class)
abstract class RegionFileVersionMixin {
    @Inject(method = "wrap(Ljava/io/InputStream;)Ljava/io/InputStream;", at = @At("HEAD"), cancellable = true)
    private void ferrum$wrapInput(InputStream in, CallbackInfoReturnable<InputStream> cir) throws IOException {
        if ((Object) this == RegionFileVersion.VERSION_DEFLATE) {
            cir.setReturnValue(StreamCompression.inflate(in));
        } else if ((Object) this == RegionFileVersion.VERSION_GZIP) {
            cir.setReturnValue(StreamCompression.gunzip(in));
        }
    }

    @Inject(method = "wrap(Ljava/io/OutputStream;)Ljava/io/OutputStream;", at = @At("HEAD"), cancellable = true)
    private void ferrum$wrapOutput(OutputStream out, CallbackInfoReturnable<OutputStream> cir) {
        if ((Object) this == RegionFileVersion.VERSION_DEFLATE) {
            cir.setReturnValue(StreamCompression.deflating(out, false));
        } else if ((Object) this == RegionFileVersion.VERSION_GZIP) {
            cir.setReturnValue(StreamCompression.deflating(out, true));
        }
    }
}
