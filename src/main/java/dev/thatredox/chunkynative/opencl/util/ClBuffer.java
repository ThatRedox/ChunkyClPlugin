package dev.thatredox.chunkynative.opencl.util;

import dev.thatredox.chunkynative.common.export.Packer;
import dev.thatredox.chunkynative.opencl.renderer.RendererInstance;
import static org.jocl.CL.*;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import org.jocl.*;

public class ClBuffer implements ClResource {
    private final cl_mem buffer;
    private boolean valid;

    public ClBuffer(int[] buffer) {
        if (buffer.length == 0) {
            buffer = new int[1];
        }

        RendererInstance instance = RendererInstance.get();
        this.buffer = clCreateBuffer(instance.context, CL_MEM_READ_ONLY | CL_MEM_COPY_HOST_PTR,
                (long) Sizeof.cl_uint * buffer.length, Pointer.to(buffer), null);
        this.valid = true;
    }

    public ClBuffer(IntArrayList buffer) {
        if (buffer.size() == 0) {
            buffer = new IntArrayList(new int[1]);
        }

        RendererInstance instance = RendererInstance.get();
        this.buffer = clCreateBuffer(instance.context, CL_MEM_READ_ONLY | CL_MEM_COPY_HOST_PTR,
                (long) Sizeof.cl_uint * buffer.size(), Pointer.to(buffer.elements()), null);
        this.valid = true;
    }

    public ClBuffer(Packer packable) {
        this(packable.pack());
    }

    public static ClBuffer singletonBuffer(int value) {
        return new ClBuffer(new int[] {value});
    }

    public cl_mem get() {
        if (valid) {
            return buffer;
        } else {
            throw new NullPointerException("Attempted to get invalid buffer.");
        }
    }

    public void set(int[] values, int offset) {
        RendererInstance instance = RendererInstance.get();
        clEnqueueWriteBuffer(instance.commandQueue, this.get(), CL_TRUE, (long) Sizeof.cl_uint * offset,
                (long) Sizeof.cl_uint * values.length, Pointer.to(values),
                0, null, null);
    }

    public void release() {
        if (valid) {
            valid = false;
            clReleaseMemObject(buffer);
        }
    }

    @Override
    protected void finalize() {
        this.release();
    }
}
