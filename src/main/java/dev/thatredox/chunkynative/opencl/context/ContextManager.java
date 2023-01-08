package dev.thatredox.chunkynative.opencl.context;

import org.jocl.CLException;
import org.jocl.cl_program;
import se.llbit.log.Log;

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
        try {
            instance = new ContextManager(device);
        } catch (CLException e) {
            Log.error("Failed to set device", e);
        }
    }

    public static synchronized void reload() {
        setDevice(instance.device);
    }

    public static class Tonemap {
        public final cl_program simpleFilter;

        private Tonemap(ClContext context) {
            this.simpleFilter = KernelLoader.loadProgram(context, "tonemap", "post_processing_filter.cl");
        }
    }
}
