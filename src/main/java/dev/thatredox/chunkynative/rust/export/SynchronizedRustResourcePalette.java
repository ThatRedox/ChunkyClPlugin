package dev.thatredox.chunkynative.rust.export;

import dev.thatredox.chunkynative.util.NativeCleaner;

public class SynchronizedRustResourcePalette implements AutoCloseable {
    private final NativeCleaner.Cleaner cleaner;
    private long address;

    public SynchronizedRustResourcePalette() {
        long addr = SynchronizedRustResourcePalette.create();
        // Safety: Cleaner will only ever clean once. If this cleaner is naturally called, the object is long out of
        //         scope and only accessible through the leaked address.
        cleaner = NativeCleaner.INSTANCE.register(this, () -> SynchronizedRustResourcePalette.drop(addr));
        address = addr;
    }

    public synchronized long put(int[] resource) {
        if (isDropped()) {
            throw new IllegalStateException("Resource was dropped!");
        }
        return put(address, resource);
    }

    /**
     * Safety: This address is only valid while this object is alive and not closed.
     *         This should only be used right before a JNI call.
     */
    public synchronized long getAddress() {
        if (isDropped()) {
            throw new IllegalStateException("Resource was dropped!");
        }
        return address;
    }

    @Override
    public synchronized void close() {
        address = 0;
        cleaner.clean();
    }

    public synchronized boolean isDropped() {
        return address == 0;
    }

    private static native long create();
    private static native void drop(long address);
    private static native long put(long address, int[] resource);
}
