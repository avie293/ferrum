package cc.avie.ferrum.mixin.network.encryption;

import cc.avie.ferrum.natives.Cfb8Cipher;
import cc.avie.ferrum.network.NativeEncryption;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToByteEncoder;
import javax.crypto.Cipher;
import net.minecraft.network.CipherEncoder;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Typed with Object so allocateBuffer overrides the erased method directly; Mixin does not merge the bridge method
// javac would generate for a ByteBuf parameter.
@Mixin(CipherEncoder.class)
abstract class CipherEncoderMixin extends MessageToByteEncoder<Object> {
    @Unique
    private @Nullable Cfb8Cipher ferrum$cipher;

    @Inject(method = "<init>", at = @At("TAIL"))
    private void ferrum$createNativeCipher(Cipher cipher, CallbackInfo ci) {
        this.ferrum$cipher = NativeEncryption.take(cipher, true);
    }

    @Inject(
            method = "encode(Lio/netty/channel/ChannelHandlerContext;Lio/netty/buffer/ByteBuf;Lio/netty/buffer/ByteBuf;)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private void ferrum$encode(ChannelHandlerContext ctx, ByteBuf msg, ByteBuf out, CallbackInfo ci) {
        Cfb8Cipher cipher = this.ferrum$cipher;
        if (cipher != null) {
            NativeEncryption.encrypt(cipher, ctx, msg, out);
            ci.cancel();
        }
    }

    @Override
    protected ByteBuf allocateBuffer(ChannelHandlerContext ctx, Object msg, boolean preferDirect) throws Exception {
        if (this.ferrum$cipher != null) {
            return NativeEncryption.allocateOutput(ctx, (ByteBuf) msg);
        }
        return super.allocateBuffer(ctx, msg, preferDirect);
    }

    @Override
    public void handlerRemoved(ChannelHandlerContext ctx) throws Exception {
        if (this.ferrum$cipher != null) {
            this.ferrum$cipher.close();
        }
        super.handlerRemoved(ctx);
    }
}
