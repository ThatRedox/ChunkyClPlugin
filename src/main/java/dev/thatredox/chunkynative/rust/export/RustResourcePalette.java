package dev.thatredox.chunkynative.rust.export;

import dev.thatredox.chunkynative.common.export.Packer;
import dev.thatredox.chunkynative.common.export.ResourcePalette;

public class RustResourcePalette<T extends Packer> implements ResourcePalette<T>, AutoCloseable {
    private final SynchronizedRustResourcePalette mem;

    public RustResourcePalette() {
        mem = new SynchronizedRustResourcePalette();
    }

    @Override
    public synchronized int put(T resource) {
        long index = this.mem.put(resource.pack().toIntArray());
        if (index < 0 || index >= Integer.MAX_VALUE) {
            throw new RuntimeException("Too many resources");
        }
        return (int) index;
    }

    @Override
    public synchronized void release() {
        mem.close();
    }

    @Override
    public synchronized void close() {
        mem.close();
    }
}
