package dev.thatredox.chunkynative.opencl.tonemap;

import dev.thatredox.chunkynative.opencl.renderer.RendererInstance;
import org.jocl.Pointer;
import org.jocl.Sizeof;
import se.llbit.chunky.renderer.postprocessing.PostProcessingFilter;

import static org.jocl.CL.*;

public class ImposterCombinationGpuPostProcessingFilter extends ImposterGpuPostProcessingFilter {
    public enum Filter {
        GAMMA(0),
        TONEMAP1(1),
        ACES(2),
        HABLE(3);

        public final int id;
        Filter(int id) {
            this.id = id;
        }
    }

    public ImposterCombinationGpuPostProcessingFilter(PostProcessingFilter imposter, String kernelName, String entryPoint, Filter filter, RendererInstance instance) {
        super(imposter, kernelName, entryPoint,
                kernel -> clSetKernelArg(kernel, 5, Sizeof.cl_int, Pointer.to(new int[] { filter.id })),
                instance);
    }
}
