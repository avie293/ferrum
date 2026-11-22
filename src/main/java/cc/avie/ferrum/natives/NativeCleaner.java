package cc.avie.ferrum.natives;

import java.lang.ref.Cleaner;

/**
 * Frees native objects that were never closed explicitly, for example when a connection is dropped before its
 * handlers are removed.
 */
final class NativeCleaner {
    static final Cleaner CLEANER = Cleaner.create();

    private NativeCleaner() {
    }
}
