package dev.thatredox.chunkynative.opencl.renderer.export;

import dev.thatredox.chunkynative.common.export.Packer;
import dev.thatredox.chunkynative.common.export.ResourcePalette;
import dev.thatredox.chunkynative.opencl.util.ClBuffer;
import dev.thatredox.chunkynative.opencl.util.ClResource;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import org.jocl.cl_mem;

public class ClPackedResourcePalette<T extends Packer> implements ResourcePalette<T>, ClResource {
    protected ClBuffer buffer = null;
    protected IntArrayList palette = new IntArrayList();
    protected boolean locked = false;

    @Override
    public int put(T resource) {
        if (locked) throw new IllegalStateException("Attempted to modify a locked palette.");
        int ptr = palette.size();
        palette.addAll(resource.pack());
        return ptr;
    }

    public ClBuffer build() {
        if (buffer == null) {
            buffer = new ClBuffer(palette);
            locked = true;
        }
        assert locked;
        return buffer;
    }

    public cl_mem get() {
        return build().get();
    }

    public void release() {
        if (buffer != null) {
            buffer.release();
            buffer = null;
        }
    }
}
