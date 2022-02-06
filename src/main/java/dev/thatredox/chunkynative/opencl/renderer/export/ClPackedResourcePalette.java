package dev.thatredox.chunkynative.opencl.renderer.export;

import dev.thatredox.chunkynative.common.export.Packer;
import dev.thatredox.chunkynative.common.export.ResourcePalette;
import dev.thatredox.chunkynative.opencl.util.ClIntBuffer;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import org.jocl.cl_mem;

public class ClPackedResourcePalette<T extends Packer> implements ResourcePalette<T>, AutoCloseable {
    protected ClIntBuffer buffer = null;
    protected IntArrayList palette = new IntArrayList();

    @Override
    public int put(T resource) {
        if (buffer != null) throw new IllegalStateException("Attempted to modify a locked palette.");
        int ptr = palette.size();
        palette.addAll(resource.pack());
        return ptr;
    }

    public ClIntBuffer build() {
        if (buffer == null) {
            buffer = new ClIntBuffer(palette);
        }
        return buffer;
    }

    public cl_mem get() {
        return build().get();
    }

    @Override
    public void close() {
        buffer.close();
    }
}
