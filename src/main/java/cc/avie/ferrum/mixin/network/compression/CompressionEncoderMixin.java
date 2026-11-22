package cc.avie.ferrum.mixin.network.compression;

import cc.avie.ferrum.natives.ZlibDeflater;
import cc.avie.ferrum.network.NativeCompression;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToByteEncoder;
import java.util.zip.Deflater;
import net.minecraft.network.CompressionEncoder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Typed with Object so allocateBuffer overrides the erased method directly; Mixin does not merge the bridge method
// javac would generate for a ByteBuf parameter.
@Mixin(CompressionEncoder.class)
abstract class CompressionEncoderMixin extends MessageToByteEncoder<Object> {
    @Shadow
    private int threshold;

    @Unique
    private final ZlibDeflater ferrum$deflater = new ZlibDeflater(Deflater.DEFAULT_COMPRESSION);

    /**
     * The JDK deflater is never used, so don't allocate its native state.
     */
    @WrapOperation(method = "<init>", at = @At(value = "NEW", target = "()Ljava/util/zip/Deflater;"))
    private Deflater ferrum$skipJdkDeflater(Operation<Deflater> original) {
        return null;
    }

    @Inject(method = "encode(Lio/netty/channel/ChannelHandlerContext;Lio/netty/buffer/ByteBuf;Lio/netty/buffer/ByteBuf;)V",
            at = @At("HEAD"), cancellable = true)
    private void ferrum$encode(ChannelHandlerContext ctx, ByteBuf uncompressed, ByteBuf out, CallbackInfo ci) {
        NativeCompression.encode(this.ferrum$deflater, this.threshold, ctx, uncompressed, out);
        ci.cancel();
    }

    @Override
    protected ByteBuf allocateBuffer(ChannelHandlerContext ctx, Object msg, boolean preferDirect) {
        return NativeCompression.allocateOutput(ctx, (ByteBuf) msg, this.threshold);
    }

    @Override
    public void handlerRemoved(ChannelHandlerContext ctx) throws Exception {
        this.ferrum$deflater.close();
        super.handlerRemoved(ctx);
    }
}
