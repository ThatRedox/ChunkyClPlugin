package dev.thatredox.chunkynative.rust.export.ffi;

public class SynchronizedRustBvh extends AbstractSynchronizedRustResource {
    public SynchronizedRustBvh(int[] bvh) {
        this.init(create(bvh), SynchronizedRustBvh::drop);
    }

    private static native long create(int[] bvh);
    private static native void drop(long address);
}
