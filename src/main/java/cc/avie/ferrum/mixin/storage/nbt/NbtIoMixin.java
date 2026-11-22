package cc.avie.ferrum.mixin.storage.nbt;

import cc.avie.ferrum.storage.StreamCompression;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import net.minecraft.nbt.NbtIo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Handles gzip compressed NBT files, such as {@code level.dat}, player data and structure templates, natively.
 */
@Mixin(NbtIo.class)
abstract class NbtIoMixin {
    @Inject(method = "createDecompressorStream", at = @At("HEAD"), cancellable = true)
    private static void ferrum$gunzip(InputStream in, CallbackInfoReturnable<DataInputStream> cir) throws IOException {
        cir.setReturnValue(new DataInputStream(StreamCompression.gunzip(in)));
    }

    @Inject(method = "createCompressorStream", at = @At("HEAD"), cancellable = true)
    private static void ferrum$gzip(OutputStream out, CallbackInfoReturnable<DataOutputStream> cir) {
        cir.setReturnValue(new DataOutputStream(StreamCompression.deflating(out, true)));
    }
}
