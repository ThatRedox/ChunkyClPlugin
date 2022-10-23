package dev.thatredox.chunkynative.rust.export;

import dev.thatredox.chunkynative.common.export.Packer;
import dev.thatredox.chunkynative.common.export.ResourcePalette;

public class RustResourcePalette<T extends Packer> implements ResourcePalette<T>, AutoCloseable {
    private final long address;
    private boolean valid;

    public RustResourcePalette() {
        address = RustResourcePalette.create();
        valid = true;
    }

    @Override
    public synchronized int put(T resource) {
        if (!valid) {
            throw new IllegalStateException("Palette was dropped!");
        }
        int index = this.put_resource(address, resource.pack().toIntArray());
        if (index < 0) {
            throw new RuntimeException("Too many resources");
        }
        return index;
    }

    @Override
    public synchronized void release() {
        if (valid) {
            valid = false;
            this.drop(address);
        }
    }

    @Override
    public synchronized void close() {
        if (valid) {
            valid = false;
            this.drop(address);
        }
    }

    private static native long create();
    private native void drop(long address);
    private native int put_resource(long address, int[] resource);

    public synchronized void test() {
        if (!valid) {
            throw new IllegalStateException("Palette was dropped!");
        }
        this.test_impl(address);
    }
    private native void test_impl(long address);
}
