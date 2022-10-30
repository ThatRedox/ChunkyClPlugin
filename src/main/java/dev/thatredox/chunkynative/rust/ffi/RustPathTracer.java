package dev.thatredox.chunkynative.rust.ffi;

import se.llbit.math.Ray;

public class RustPathTracer extends AbstractSynchronizedRustResource {
    private final AbstractSynchronizedRustResource.AddressGuard octree;
    private long address;

    public RustPathTracer(AbstractSynchronizedRustResource.AddressGuard octree) {
        address = create(octree.address);
        this.init(address, RustPathTracer::drop);
        this.octree = octree;
    }

    @Override
    public void close() {
        octree.close();
        address = 0;
        super.close();
    }

    public void trace(Ray ray, long seed, double[] rgb) {
        assert rgb.length == 3;
        if (address == 0) {
            throw new NullPointerException();
        }
        trace(address, ray.o.x, ray.o.y, ray.o.z, ray.d.x, ray.d.y, ray.d.z, seed, rgb);
    }

    private static native long create(long octree);
    private static native long drop(long address);
    private static native void trace(long address, double ox, double oy, double oz, double dx, double dy, double dz, long seed, double[] rgb);
}
