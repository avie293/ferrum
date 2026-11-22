package cc.avie.ferrum.config;

import cc.avie.ferrum.Ferrum;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import net.fabricmc.loader.api.FabricLoader;

/**
 * Ferrum's options, stored in {@code config/ferrum.properties}.
 *
 * <p>Every optimization has an option of the same name as its mixin package (for example {@code network.compression}
 * for {@code cc.avie.ferrum.mixin.network.compression}). The file is read once while mixins are being applied, so
 * changes take effect after a restart.
 */
public final class FerrumConfig {
    private static final String FILE_NAME = Ferrum.MOD_ID + ".properties";

    /**
     * All options with a short description, in file order.
     */
    private static final Map<String, String> OPTIONS = new LinkedHashMap<>();

    static {
        OPTIONS.put("network.compression", "Compress and decompress network packets with the native zlib.");
        OPTIONS.put("network.encryption", "Encrypt and decrypt online-mode connections with the native AES.");
        OPTIONS.put("storage.compression", "Compress and decompress chunks in region files with the native zlib.");
        OPTIONS.put("texture.mipmaps", "Compute the alpha coverage of cutout mipmaps natively (client only).");
    }

    private final Map<String, Boolean> values;

    private FerrumConfig(Map<String, Boolean> values) {
        this.values = values;
    }

    /**
     * {@return whether the given option is enabled; unknown options are treated as enabled}
     */
    public boolean isEnabled(String option) {
        return this.values.getOrDefault(option, true);
    }

    public static FerrumConfig load() {
        Path path = FabricLoader.getInstance().getConfigDir().resolve(FILE_NAME);
        Map<String, Boolean> values = new LinkedHashMap<>();
        boolean rewrite = !Files.exists(path);

        if (!rewrite) {
            Properties properties = new Properties();
            try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
                properties.load(reader);
            } catch (IOException | IllegalArgumentException e) {
                Ferrum.LOGGER.warn("Failed to read {}, resetting it to the defaults", path, e);
                properties.clear();
                rewrite = true;
            }

            for (String option : OPTIONS.keySet()) {
                String value = properties.getProperty(option);
                if (value == null) {
                    rewrite = true;
                    continue;
                }
                switch (value.trim().toLowerCase(Locale.ROOT)) {
                    case "true" -> values.put(option, true);
                    case "false" -> values.put(option, false);
                    default -> {
                        Ferrum.LOGGER.warn("Invalid value '{}' for {} in {}, using true", value, option, path);
                        rewrite = true;
                    }
                }
            }
        }

        if (rewrite) {
            write(path, values);
        }
        return new FerrumConfig(values);
    }

    private static void write(Path path, Map<String, Boolean> values) {
        StringBuilder out = new StringBuilder()
                .append("# Ferrum configuration. Changes take effect after restarting the game.\n")
                .append("# Every optimization is enabled by default; set one to false if it causes problems.\n");
        for (Map.Entry<String, String> option : OPTIONS.entrySet()) {
            out.append('\n')
                    .append("# ").append(option.getValue()).append('\n')
                    .append(option.getKey()).append('=').append(values.getOrDefault(option.getKey(), true))
                    .append('\n');
        }

        try {
            Files.createDirectories(path.getParent());
            Files.writeString(path, out, StandardCharsets.UTF_8);
        } catch (IOException e) {
            Ferrum.LOGGER.warn("Failed to write {}", path, e);
        }
    }
}
