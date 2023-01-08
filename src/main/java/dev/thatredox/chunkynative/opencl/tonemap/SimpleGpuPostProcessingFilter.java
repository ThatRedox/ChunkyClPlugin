package dev.thatredox.chunkynative.opencl.tonemap;

import dev.thatredox.chunkynative.opencl.context.ContextManager;
import dev.thatredox.chunkynative.opencl.util.ClMemory;
import org.jocl.*;
import se.llbit.chunky.renderer.postprocessing.PostProcessingFilter;
import se.llbit.chunky.resources.BitmapImage;
import se.llbit.util.TaskTracker;

import java.util.function.Consumer;

import static org.jocl.CL.*;
import static org.jocl.CL.clReleaseKernel;

public class SimpleGpuPostProcessingFilter implements PostProcessingFilter {
    private final String name;
    private final String description;
    private final String id;

    private final String entryPoint;
    private final Consumer<cl_kernel> argumentConsumer;

    public SimpleGpuPostProcessingFilter(String name, String description, String id,
                                         String entryPoint, Consumer<cl_kernel> argumentConsumer) {
        this.name = name;
        this.description = description;
        this.id = id;

        this.argumentConsumer = argumentConsumer;
        this.entryPoint = entryPoint;
    }

    @Override
    public void processFrame(int width, int height, double[] input, BitmapImage output, double exposure, TaskTracker.Task task) {
        ContextManager ctx = ContextManager.get();
        cl_kernel kernel = clCreateKernel(ctx.tonemap.simpleFilter, entryPoint, null);
        try (
                ClMemory inputMem = new ClMemory(clCreateBuffer(ctx.context.context, CL_MEM_READ_ONLY | CL_MEM_COPY_HOST_PTR,
                        (long) Sizeof.cl_ulong * input.length, Pointer.to(input), null));
                ClMemory outputMem = new ClMemory(clCreateBuffer(ctx.context.context, CL_MEM_WRITE_ONLY,
                        (long) Sizeof.cl_int * output.data.length, null, null));
        ) {
            clSetKernelArg(kernel, 0, Sizeof.cl_int, Pointer.to(new int[] {width}));
            clSetKernelArg(kernel, 1, Sizeof.cl_int, Pointer.to(new int[] {height}));
            clSetKernelArg(kernel, 2, Sizeof.cl_float, Pointer.to(new float[] {(float) exposure}));
            clSetKernelArg(kernel, 3, Sizeof.cl_mem, Pointer.to(inputMem.get()));
            clSetKernelArg(kernel, 4, Sizeof.cl_mem, Pointer.to(outputMem.get()));
            argumentConsumer.accept(kernel);

            cl_event event = new cl_event();
            clEnqueueNDRangeKernel(ctx.context.queue, kernel, 1, null,
                    new long[] {output.data.length}, null, 0, null,
                    event);
            clEnqueueReadBuffer(ctx.context.queue, outputMem.get(), CL_TRUE, 0,
                    (long) Sizeof.cl_int * output.data.length, Pointer.to(output.data),
                    1, new cl_event[] {event}, null);
        } finally {
            clReleaseKernel(kernel);
        }
    }

    @Override
    public String getName() {
        return this.name;
    }

    @Override
    public String getDescription() {
        return this.description;
    }

    @Override
    public String getId() {
        return this.id;
    }
}
