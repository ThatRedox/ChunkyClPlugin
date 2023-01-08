package dev.thatredox.chunkynative.opencl.context;

import org.jocl.cl_program;

public class ContextManager {
    public final Device device;
    public final ClContext context;
    public final Tonemap tonemap;

    private static volatile ContextManager instance = new ContextManager(Device.getPreferredDevice());

    private ContextManager(Device device) {
        this.device = device;
        this.context = new ClContext(device);
        this.tonemap = new Tonemap(context);
    }

    public static ContextManager get() {
        return instance;
    }

    public static synchronized void setDevice(Device device) {
        instance = new ContextManager(device);
    }

    public static synchronized void reload() {
        instance = new ContextManager(instance.device);
    }

    public static class Tonemap {
        public final cl_program simpleFilter;

        private Tonemap(ClContext context) {
            this.simpleFilter = KernelLoader.loadProgram(context, "tonemap", "post_processing_filter.cl");
        }
    }
}
