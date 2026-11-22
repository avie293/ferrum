package cc.avie.ferrum.mixin.network.compression;

import cc.avie.ferrum.network.NativeCompression;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import java.util.List;
import java.util.zip.Inflater;
import net.minecraft.network.CompressionDecoder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(CompressionDecoder.class)
abstract class CompressionDecoderMixin {
    @Shadow
    private int threshold;
    @Shadow
    private boolean validateDecompressed;

    /**
     * The JDK inflater is never used, so don't allocate its native state.
     */
    @WrapOperation(method = "<init>", at = @At(value = "NEW", target = "()Ljava/util/zip/Inflater;"))
    private Inflater ferrum$skipJdkInflater(Operation<Inflater> original) {
        return null;
    }

    @Inject(method = "decode", at = @At("HEAD"), cancellable = true)
    private void ferrum$decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out, CallbackInfo ci)
            throws Exception {
        NativeCompression.decode(this.threshold, this.validateDecompressed, ctx, in, out);
        ci.cancel();
    }
}
