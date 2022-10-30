package dev.thatredox.chunkynative.rust.export.ffi;

public class SynchronizedRustResourcePalette extends AbstractSynchronizedRustResource {
    public SynchronizedRustResourcePalette() {
        this.init(create(), SynchronizedRustResourcePalette::drop);
    }

    public synchronized long put(int[] resource) {
        try (AddressGuard guard = this.getAddress()) {
            return put(guard.address, resource);
        }
    }

    private static native long create();
    private static native void drop(long address);
    private static native long put(long address, int[] resource);
}
