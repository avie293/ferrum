package cc.avie.ferrum;

import cc.avie.ferrum.config.FerrumConfig;
import cc.avie.ferrum.natives.NativeLibrary;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.fabricmc.loader.api.FabricLoader;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

/**
 * Applies a mixin only if its option is enabled, the native library is available and no mod that replaces the same
 * code is installed. The option of a mixin is its package relative to {@code cc.avie.ferrum.mixin}.
 */
public final class FerrumMixinPlugin implements IMixinConfigPlugin {
    private static final String MIXIN_PACKAGE = "cc.avie.ferrum.mixin.";

    /**
     * Mods that replace the code an option optimizes. Applying both would at best do the work twice.
     */
    private static final Map<String, List<String>> CONFLICTS = Map.of(
            "network.compression", List.of("krypton"),
            "network.encryption", List.of("krypton")
    );

    private FerrumConfig config;
    private boolean nativesAvailable;

    @Override
    public void onLoad(String mixinPackage) {
        this.config = FerrumConfig.load();
        this.nativesAvailable = NativeLibrary.isAvailable();

        for (Map.Entry<String, List<String>> entry : CONFLICTS.entrySet()) {
            for (String modId : entry.getValue()) {
                if (this.config.isEnabled(entry.getKey()) && FabricLoader.getInstance().isModLoaded(modId)) {
                    Ferrum.LOGGER.info("Disabling {} because {} is installed", entry.getKey(), modId);
                }
            }
        }
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        if (!this.nativesAvailable || !mixinClassName.startsWith(MIXIN_PACKAGE)) {
            return false;
        }

        String option = mixinClassName.substring(MIXIN_PACKAGE.length(), mixinClassName.lastIndexOf('.'));
        if (!this.config.isEnabled(option)) {
            return false;
        }
        for (String modId : CONFLICTS.getOrDefault(option, List.of())) {
            if (FabricLoader.getInstance().isModLoaded(modId)) {
                return false;
            }
        }
        return true;
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {
    }

    @Override
    public List<String> getMixins() {
        return null;
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }
}
