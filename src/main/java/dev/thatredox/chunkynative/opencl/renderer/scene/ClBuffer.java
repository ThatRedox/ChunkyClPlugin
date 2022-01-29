package dev.thatredox.chunkynative.opencl.renderer.scene;

import dev.thatredox.chunkynative.opencl.renderer.RendererInstance;
import static org.jocl.CL.*;

import it.unimi.dsi.fastutil.ints.IntCollection;
import org.jocl.*;

public class ClBuffer {
    private final cl_mem buffer;
    private boolean valid;

    public ClBuffer(int[] buffer) {
        RendererInstance instance = RendererInstance.get();
        this.buffer = clCreateBuffer(instance.context, CL_MEM_READ_ONLY | CL_MEM_COPY_HOST_PTR,
                (long) Sizeof.cl_uint * buffer.length, Pointer.to(buffer), null);
        this.valid = true;
    }

    public ClBuffer(IntCollection buffer) {
        this(buffer.toIntArray());
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
