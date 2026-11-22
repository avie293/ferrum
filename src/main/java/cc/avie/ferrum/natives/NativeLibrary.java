package cc.avie.ferrum.natives;

import cc.avie.ferrum.Ferrum;
import java.io.IOException;
import java.io.InputStream;
import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.SymbolLookup;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.Locale;
import net.fabricmc.loader.api.FabricLoader;
import org.jspecify.annotations.Nullable;

/**
 * Loads the bundled native library and binds its functions.
 *
 * <p>The library for the current platform is extracted to {@code .ferrum/natives/<hash>/} in the game directory.
 * The directory name is derived from the library contents, so different Ferrum versions never share a file and a
 * library that is still locked by another running instance never has to be replaced.
 */
public final class NativeLibrary {
    private static final int ABI_VERSION = 4;

    private static final @Nullable SymbolLookup LOOKUP = load();

    private NativeLibrary() {
    }

    /**
     * {@return whether the native library was loaded for this platform}
     */
    public static boolean isAvailable() {
        return LOOKUP != null;
    }

    static MethodHandle bind(String name, FunctionDescriptor descriptor) {
        SymbolLookup lookup = LOOKUP;
        if (lookup == null) {
            throw new IllegalStateException("Native library is not available");
        }
        return Linker.nativeLinker().downcallHandle(lookup.findOrThrow(name), descriptor);
    }

    private static @Nullable SymbolLookup load() {
        String platform = platform();
        if (platform == null) {
            Ferrum.LOGGER.warn("No native library for {} {}, native optimizations are disabled",
                    System.getProperty("os.name"), System.getProperty("os.arch"));
            return null;
        }

        String fileName = libraryFileName(platform);
        try {
            byte[] library = readResource("/natives/" + platform + "/" + fileName);
            if (library == null) {
                Ferrum.LOGGER.warn("Native library for {} is not bundled, native optimizations are disabled", platform);
                return null;
            }

            Path path = extract(library, fileName);
            SymbolLookup lookup = SymbolLookup.libraryLookup(path, Arena.global());
            MethodHandle version = Linker.nativeLinker().downcallHandle(lookup.findOrThrow("ferrum_abi_version"),
                    FunctionDescriptor.of(ValueLayout.JAVA_INT));
            int abiVersion = (int) version.invokeExact();
            if (abiVersion != ABI_VERSION) {
                Ferrum.LOGGER.error("Native library {} has interface version {}, expected {}", path, abiVersion,
                        ABI_VERSION);
                return null;
            }
            return lookup;
        } catch (Throwable t) {
            Ferrum.LOGGER.error("Failed to load the native library, native optimizations are disabled", t);
            return null;
        }
    }

    private static @Nullable String platform() {
        String os = System.getProperty("os.name").toLowerCase(Locale.ROOT);
        String arch = System.getProperty("os.arch").toLowerCase(Locale.ROOT);

        String osName;
        if (os.contains("win")) {
            osName = "windows";
        } else if (os.contains("mac")) {
            osName = "macos";
        } else if (os.contains("linux")) {
            osName = "linux";
        } else {
            return null;
        }

        String archName = switch (arch) {
            case "amd64", "x86_64" -> "x86_64";
            case "aarch64", "arm64" -> "aarch64";
            default -> null;
        };
        return archName == null ? null : osName + "-" + archName;
    }

    private static String libraryFileName(String platform) {
        if (platform.startsWith("windows")) {
            return "ferrum.dll";
        }
        return platform.startsWith("macos") ? "libferrum.dylib" : "libferrum.so";
    }

    private static byte @Nullable [] readResource(String name) throws IOException {
        try (InputStream in = NativeLibrary.class.getResourceAsStream(name)) {
            return in == null ? null : in.readAllBytes();
        }
    }

    private static Path extract(byte[] library, String fileName) throws IOException, NoSuchAlgorithmException {
        String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(library), 0, 8);
        Path root = FabricLoader.getInstance().getGameDir().resolve(".ferrum").resolve("natives");
        Path dir = root.resolve(hash);
        Path target = dir.resolve(fileName);

        if (!isSameFile(target, library)) {
            Files.createDirectories(dir);
            Path temp = Files.createTempFile(dir, fileName, ".tmp");
            try {
                Files.write(temp, library);
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } finally {
                Files.deleteIfExists(temp);
            }
        }

        deleteStaleCopies(root, dir);
        return target;
    }

    private static boolean isSameFile(Path file, byte[] content) throws IOException {
        return Files.isRegularFile(file) && Files.size(file) == content.length
                && Arrays.equals(Files.readAllBytes(file), content);
    }

    private static void deleteStaleCopies(Path root, Path current) {
        try (DirectoryStream<Path> dirs = Files.newDirectoryStream(root, Files::isDirectory)) {
            for (Path dir : dirs) {
                if (!dir.equals(current)) {
                    deleteDirectory(dir);
                }
            }
        } catch (IOException ignored) {
            // Best effort only: a copy may still be in use by another running instance.
        }
    }

    private static void deleteDirectory(Path dir) {
        try (DirectoryStream<Path> files = Files.newDirectoryStream(dir)) {
            for (Path file : files) {
                Files.deleteIfExists(file);
            }
            Files.deleteIfExists(dir);
        } catch (IOException ignored) {
            // Locked by another instance; it is retried on the next start.
        }
    }
}
