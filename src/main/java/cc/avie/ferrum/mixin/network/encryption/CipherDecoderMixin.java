package cc.avie.ferrum.mixin.network.encryption;

import cc.avie.ferrum.natives.Cfb8Cipher;
import cc.avie.ferrum.network.NativeEncryption;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToMessageDecoder;
import java.util.List;
import javax.crypto.Cipher;
import net.minecraft.network.CipherDecoder;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(CipherDecoder.class)
abstract class CipherDecoderMixin extends MessageToMessageDecoder<ByteBuf> {
    @Unique
    private @Nullable Cfb8Cipher ferrum$cipher;

    @Inject(method = "<init>", at = @At("TAIL"))
    private void ferrum$createNativeCipher(Cipher cipher, CallbackInfo ci) {
        this.ferrum$cipher = NativeEncryption.take(cipher, false);
    }

    @Inject(method = "decode(Lio/netty/channel/ChannelHandlerContext;Lio/netty/buffer/ByteBuf;Ljava/util/List;)V",
            at = @At("HEAD"), cancellable = true)
    private void ferrum$decode(ChannelHandlerContext ctx, ByteBuf msg, List<Object> out, CallbackInfo ci) {
        Cfb8Cipher cipher = this.ferrum$cipher;
        if (cipher != null) {
            out.add(NativeEncryption.decrypt(cipher, ctx, msg));
            ci.cancel();
        }
    }

    @Override
    public void handlerRemoved(ChannelHandlerContext ctx) throws Exception {
        if (this.ferrum$cipher != null) {
            this.ferrum$cipher.close();
        }
        super.handlerRemoved(ctx);
    }
}
