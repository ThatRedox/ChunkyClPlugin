package chunkycl.renderer.scene;

import static org.jocl.CL.*;
import org.jocl.*;

import chunkycl.renderer.RendererInstance;
import se.llbit.math.PackedOctree;

public class ClOctree {
    public final cl_mem octreeData;
    public final cl_mem octreeDepth;

    public ClOctree(PackedOctree octree) {
        RendererInstance instance = RendererInstance.get();

        octreeData = clCreateBuffer(instance.context, CL_MEM_READ_ONLY | CL_MEM_COPY_HOST_PTR,
                (long) Sizeof.cl_int * octree.treeData.length, Pointer.to(octree.treeData), null);
        octreeDepth = clCreateBuffer(instance.context, CL_MEM_READ_ONLY | CL_MEM_COPY_HOST_PTR,
                Sizeof.cl_int, Pointer.to(new int[] {octree.getDepth()}), null);
    }

    public void release() {
        clReleaseMemObject(octreeData);
        clReleaseMemObject(octreeDepth);
    }
}
