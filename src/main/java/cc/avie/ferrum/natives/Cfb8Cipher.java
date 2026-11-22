package cc.avie.ferrum.natives;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.lang.ref.Cleaner;

/**
 * A native AES-128/CFB8 stream cipher for one direction of a connection.
 *
 * <p>Instances are not thread-safe. {@link #close()} frees the native state immediately; otherwise it is freed once
 * the instance becomes unreachable.
 */
public final class Cfb8Cipher implements AutoCloseable {
    private static final MethodHandle NEW = NativeLibrary.bind("ferrum_cfb8_new", FunctionDescriptor.of(
            ValueLayout.JAVA_LONG, ValueLayout.JAVA_LONG, ValueLayout.JAVA_LONG, ValueLayout.JAVA_BOOLEAN));
    private static final MethodHandle FREE = NativeLibrary.bind("ferrum_cfb8_free",
            FunctionDescriptor.ofVoid(ValueLayout.JAVA_LONG));
    private static final MethodHandle UPDATE = NativeLibrary.bind("ferrum_cfb8_update",
            FunctionDescriptor.ofVoid(ValueLayout.JAVA_LONG, ValueLayout.JAVA_LONG, ValueLayout.JAVA_LONG));

    private final long handle;
    private final Cleaner.Cleanable cleanable;
    private boolean closed;

    /**
     * @param key     the 16 byte AES key
     * @param iv      the 16 byte initialization vector
     * @param encrypt whether this cipher encrypts or decrypts
     */
    public Cfb8Cipher(byte[] key, byte[] iv, boolean encrypt) {
        if (key.length != 16 || iv.length != 16) {
            throw new IllegalArgumentException("Key and IV must be 16 bytes long");
        }

        long handle;
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment keyAndIv = arena.allocate(32);
            MemorySegment.copy(key, 0, keyAndIv, ValueLayout.JAVA_BYTE, 0, 16);
            MemorySegment.copy(iv, 0, keyAndIv, ValueLayout.JAVA_BYTE, 16, 16);
            handle = (long) NEW.invokeExact(keyAndIv.address(), keyAndIv.address() + 16, encrypt);
            keyAndIv.fill((byte) 0);
        } catch (Throwable t) {
            throw new IllegalStateException(t);
        }
        this.handle = handle;
        this.cleanable = NativeCleaner.CLEANER.register(this, () -> free(handle));
    }

    /**
     * Encrypts or decrypts {@code length} bytes at {@code address} in place, continuing the stream of earlier calls.
     */
    public void update(long address, long length) {
        if (this.closed) {
            throw new IllegalStateException("Cipher is closed");
        }
        try {
            UPDATE.invokeExact(this.handle, address, length);
        } catch (Throwable t) {
            throw new IllegalStateException(t);
        }
    }

    @Override
    public void close() {
        this.closed = true;
        this.cleanable.clean();
    }

    private static void free(long handle) {
        try {
            FREE.invokeExact(handle);
        } catch (Throwable t) {
            throw new IllegalStateException(t);
        }
    }
}
