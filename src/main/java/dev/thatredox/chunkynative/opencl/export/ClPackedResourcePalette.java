package dev.thatredox.chunkynative.opencl.export;

import dev.thatredox.chunkynative.common.export.Packer;
import dev.thatredox.chunkynative.common.export.ResourcePalette;
import dev.thatredox.chunkynative.opencl.util.ClBuffer;
import dev.thatredox.chunkynative.opencl.util.ClResource;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import org.jocl.cl_mem;

public class ClPackedResourcePalette<T extends Packer> implements ResourcePalette<T>, ClResource {
    protected ClBuffer buffer = null;
    protected IntArrayList palette = new IntArrayList();

    @Override
    public int put(T resource) {
        int ptr = palette.size();
        palette.addAll(resource.pack());
        return ptr;
    }

    public ClBuffer build() {
        if (buffer == null) {
            buffer = new ClBuffer(palette);
        }
        return buffer;
    }

    public cl_mem get() {
        return build().get();
    }

    public void release() {
        if (buffer != null) {
            buffer.release();
        }
    }
}
