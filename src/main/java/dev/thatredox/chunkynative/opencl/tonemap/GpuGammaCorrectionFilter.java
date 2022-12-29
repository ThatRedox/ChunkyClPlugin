package dev.thatredox.chunkynative.opencl.tonemap;

import dev.thatredox.chunkynative.opencl.renderer.KernelLoader;
import dev.thatredox.chunkynative.opencl.renderer.RendererInstance;
import dev.thatredox.chunkynative.opencl.util.ClMemory;
import org.jocl.*;
import se.llbit.chunky.renderer.postprocessing.PostProcessingFilter;
import se.llbit.chunky.resources.BitmapImage;
import se.llbit.util.TaskTracker;

import static org.jocl.CL.*;

public class GpuGammaCorrectionFilter implements PostProcessingFilter {
    private final cl_program filter;
    private final RendererInstance instance;

    public GpuGammaCorrectionFilter(RendererInstance instance) {
        this.instance = instance;
        filter = KernelLoader.loadProgram("tonemap", "gamma_correction_filter.cl", instance.context,
                new cl_device_id[] { instance.device });
    }

    @Override
    public void processFrame(int width, int height, double[] input, BitmapImage output, double exposure, TaskTracker.Task task) {
        long start = System.currentTimeMillis();
        cl_kernel kernel = clCreateKernel(filter, "filter", null);
        try (
            ClMemory inputMem = new ClMemory(clCreateBuffer(instance.context, CL_MEM_READ_ONLY | CL_MEM_COPY_HOST_PTR,
                    (long) Sizeof.cl_ulong * input.length, Pointer.to(input), null));
            ClMemory outputMem = new ClMemory(clCreateBuffer(instance.context, CL_MEM_WRITE_ONLY,
                    (long) Sizeof.cl_int * output.data.length, null, null));
        ) {
            clSetKernelArg(kernel, 0, Sizeof.cl_int, Pointer.to(new int[] {width}));
            clSetKernelArg(kernel, 1, Sizeof.cl_int, Pointer.to(new int[] {height}));
            clSetKernelArg(kernel, 2, Sizeof.cl_float, Pointer.to(new float[] {(float) exposure}));
            clSetKernelArg(kernel, 3, Sizeof.cl_mem, Pointer.to(inputMem.get()));
            clSetKernelArg(kernel, 4, Sizeof.cl_mem, Pointer.to(outputMem.get()));

            cl_event event = new cl_event();
            clEnqueueNDRangeKernel(instance.commandQueue, kernel, 1, null,
                    new long[] {output.data.length}, null, 0, null,
                    event);
            clEnqueueReadBuffer(instance.commandQueue, outputMem.get(), CL_TRUE, 0,
                    (long) Sizeof.cl_int * output.data.length, Pointer.to(output.data),
                    1, new cl_event[] {event}, null);
        } finally {
            clReleaseKernel(kernel);
        }
        long stop = System.currentTimeMillis();
        System.out.println("GPU gamma correction took: " + (stop - start) + "ms");
    }

    @Override
    public String getName() {
        return "GPU Gamma Correction Filter";
    }

    @Override
    public String getDescription() {
        return "Gamma correction filter on GPU.";
    }

    @Override
    public String getId() {
        return "GPU_GAMMA_CORRECTION";
    }
}
