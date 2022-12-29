package dev.thatredox.chunkynative.opencl.tonemap;

import dev.thatredox.chunkynative.opencl.renderer.RendererInstance;
import org.jocl.cl_kernel;
import se.llbit.chunky.renderer.postprocessing.PostProcessingFilter;

import java.util.function.Consumer;

public class ImposterGpuPostProcessingFilter extends GpuPostProcessingFilter{
    public ImposterGpuPostProcessingFilter(PostProcessingFilter imposter, String kernelName, String entryPoint, Consumer<cl_kernel> argumentConsumer, RendererInstance instance) {
        super(imposter.getName(), imposter.getDescription(), imposter.getId(), kernelName, entryPoint, argumentConsumer, instance);
    }
}
