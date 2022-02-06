package dev.thatredox.chunkynative.opencl.util;

import dev.thatredox.chunkynative.common.export.Packer;
import dev.thatredox.chunkynative.opencl.renderer.RendererInstance;

import static org.jocl.CL.*;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import org.jocl.*;

public class ClIntBuffer implements AutoCloseable {
    private final ClMemory buffer;

    public ClIntBuffer(int[] buffer, int length) {
        if (length == 0) {
            buffer = new int[1];
            length = 1;
        }
        assert buffer.length >= length;

        RendererInstance instance = RendererInstance.get();
        this.buffer = new ClMemory(
                clCreateBuffer(instance.context, CL_MEM_READ_ONLY | CL_MEM_COPY_HOST_PTR,
                (long) Sizeof.cl_uint * length, Pointer.to(buffer), null));
    }

    public ClIntBuffer(int[] buffer) {
        this(buffer, buffer.length);
    }

    public ClIntBuffer(IntArrayList buffer) {
        this(buffer.elements(), buffer.size());
    }

    public ClIntBuffer(Packer packable) {
        this(packable.pack());
    }

    public ClIntBuffer(int value) {
        this(new int[] {value});
    }

    public cl_mem get() {
        return buffer.get();
    }

    public void set(int[] values, int offset) {
        RendererInstance instance = RendererInstance.get();
        clEnqueueWriteBuffer(instance.commandQueue, this.get(), CL_TRUE, (long) Sizeof.cl_uint * offset,
                (long) Sizeof.cl_uint * values.length, Pointer.to(values),
                0, null, null);
    }

    @Override
    public void close() {
        buffer.close();
    }
}
