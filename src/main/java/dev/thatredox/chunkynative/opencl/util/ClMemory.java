package dev.thatredox.chunkynative.opencl.util;

import dev.thatredox.chunkynative.util.NativeCleaner;
import org.jocl.CL;
import org.jocl.cl_mem;

public class ClMemory implements AutoCloseable {
    protected final NativeCleaner.Cleaner cleaner;
    protected final cl_mem memory;
    protected boolean valid;

    public ClMemory(cl_mem memory) {
        this.cleaner = NativeCleaner.INSTANCE.register(this, () -> CL.clReleaseMemObject(memory));
        this.memory = memory;
        this.valid = true;
    }

    public cl_mem get() {
        if (valid) {
            return memory;
        } else {
            throw new NullPointerException("Attempted to access invalid CL_MEM: " + this.memory.toString());
        }
    }

    @Override
    public void close() {
        this.cleaner.clean();
    }
}
