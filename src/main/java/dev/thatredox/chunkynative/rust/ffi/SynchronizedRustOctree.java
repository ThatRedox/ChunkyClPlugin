package dev.thatredox.chunkynative.rust.ffi;

public class SynchronizedRustOctree extends AbstractSynchronizedRustResource {
    public SynchronizedRustOctree(int[] octree, int depth, int[] blockMapping) {
        this.init(create(octree, depth, blockMapping), SynchronizedRustOctree::drop);
    }

    private static native long create(int[] octree, int depth, int[] blockMapping);
    private static native void drop(long address);
}
