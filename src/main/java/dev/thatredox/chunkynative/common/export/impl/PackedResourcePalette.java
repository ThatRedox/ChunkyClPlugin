package dev.thatredox.chunkynative.common.export.impl;

import dev.thatredox.chunkynative.common.export.Packer;
import it.unimi.dsi.fastutil.ints.IntArrayList;

public class PackedResourcePalette<T extends Packer> extends CachedResourcePalette<T> implements Packer {
    protected final IntArrayList palette = new IntArrayList();

    @Override
    protected int putImpl(T resource) {
        int ptr = palette.size();
        palette.addAll(resource.pack());
        return ptr;
    }

    @Override
    public IntArrayList pack() {
        this.lock();
        return palette;
    }
}
