package cc.avie.ferrum.mixin.network.encryption;

import cc.avie.ferrum.network.NativeEncryption;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.sugar.Local;
import java.security.Key;
import javax.crypto.Cipher;
import net.minecraft.util.Crypt;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(Crypt.class)
abstract class CryptMixin {
    @ModifyReturnValue(method = "getCipher", at = @At("RETURN"))
    private static Cipher ferrum$rememberKey(Cipher cipher, @Local(argsOnly = true) Key key) {
        NativeEncryption.remember(cipher, key);
        return cipher;
    }
}
