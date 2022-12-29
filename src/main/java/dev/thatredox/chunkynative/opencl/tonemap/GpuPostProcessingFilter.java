package dev.thatredox.chunkynative.opencl.tonemap;

import dev.thatredox.chunkynative.opencl.renderer.KernelLoader;
import dev.thatredox.chunkynative.opencl.renderer.RendererInstance;
import dev.thatredox.chunkynative.opencl.util.ClMemory;
import org.jocl.*;
import se.llbit.chunky.renderer.postprocessing.PostProcessingFilter;
import se.llbit.chunky.resources.BitmapImage;
import se.llbit.util.TaskTracker;

import java.util.function.Consumer;

import static org.jocl.CL.*;
import static org.jocl.CL.clReleaseKernel;

public class GpuPostProcessingFilter implements PostProcessingFilter {
    private final cl_program filter;
    private final RendererInstance instance;
    private final String entryPoint;
    private final Consumer<cl_kernel> argumentConsumer;

    private final String name;
    private final String description;
    private final String id;

    public GpuPostProcessingFilter(String name, String description, String id, String kernelName, String entryPoint,
                                   Consumer<cl_kernel> argumentConsumer, RendererInstance instance) {
        this.name = name;
        this.description = description;
        this.id = id;

        this.argumentConsumer = argumentConsumer;
        this.instance = instance;
        this.entryPoint = entryPoint;
        this.filter = KernelLoader.loadProgram("tonemap", kernelName, instance.context,
                new cl_device_id[] { instance.device });
    }

    @Override
    public void processFrame(int width, int height, double[] input, BitmapImage output, double exposure, TaskTracker.Task task) {
        cl_kernel kernel = clCreateKernel(filter, entryPoint, null);
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
            argumentConsumer.accept(kernel);

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
