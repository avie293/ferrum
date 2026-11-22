package cc.avie.ferrum.natives;

import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;

/**
 * Bindings for the native, bit-exact port of the alpha coverage functions of {@code MipmapGenerator}.
 *
 * <p>{@code pixels} is the address of a tightly packed RGBA image, as returned by {@code NativeImage#getPointer}.
 */
public final class NativeMipmaps {
    private static final MethodHandle COVERAGE = NativeLibrary.bind("ferrum_mip_coverage",
            FunctionDescriptor.of(ValueLayout.JAVA_FLOAT, ValueLayout.JAVA_LONG, ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_INT, ValueLayout.JAVA_FLOAT, ValueLayout.JAVA_FLOAT));
    private static final MethodHandle SCALE_ALPHA = NativeLibrary.bind("ferrum_mip_scale_alpha",
            FunctionDescriptor.ofVoid(ValueLayout.JAVA_LONG, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_FLOAT, ValueLayout.JAVA_FLOAT, ValueLayout.JAVA_FLOAT));

    private NativeMipmaps() {
    }

    public static float alphaTestCoverage(long pixels, int width, int height, float alphaRef, float alphaScale) {
        try {
            return (float) COVERAGE.invokeExact(pixels, width, height, alphaRef, alphaScale);
        } catch (Throwable t) {
            throw new IllegalStateException(t);
        }
    }

    public static void scaleAlphaToCoverage(long pixels, int width, int height, float desiredCoverage,
            float alphaRef, float alphaCutoffBias) {
        try {
            SCALE_ALPHA.invokeExact(pixels, width, height, desiredCoverage, alphaRef, alphaCutoffBias);
        } catch (Throwable t) {
            throw new IllegalStateException(t);
        }
    }
}
