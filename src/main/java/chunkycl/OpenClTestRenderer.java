package chunkycl;

import static org.jocl.CL.*;

import chunkycl.renderer.RendererInstance;
import chunkycl.renderer.scene.ClCamera;
import org.jocl.*;

import chunkycl.renderer.scene.ClOctree;
import se.llbit.chunky.main.Chunky;
import se.llbit.chunky.renderer.DefaultRenderManager;
import se.llbit.chunky.renderer.ResetReason;
import se.llbit.log.Log;
import se.llbit.math.Octree;
import se.llbit.math.PackedOctree;

import java.util.Arrays;

public class OpenClTestRenderer extends AbstractOpenClRenderer {
    private ClOctree octree = null;
    private ClCamera camera = null;

    @Override
    public String getId() {
        return "ChunkyClTestRenderer";
    }

    @Override
    public String getName() {
        return "ChunkyClTestRenderer";
    }

    @Override
    public String getDescription() {
        return "ChunkyClTestRenderer";
    }

    @Override
    public void render(DefaultRenderManager manager) throws InterruptedException {
        RendererInstance instance = RendererInstance.get();
        double[] sampleBuffer = manager.bufferedScene.getSampleBuffer();
        float[] passBuffer = new float[sampleBuffer.length];

        if (octree == null) {
            Octree.OctreeImplementation sceneOctree = manager.bufferedScene.getWorldOctree().getImplementation();
            if (manager.bufferedScene.getWorldOctree().getImplementation() instanceof PackedOctree) {
                octree = new ClOctree((PackedOctree) manager.bufferedScene.getWorldOctree().getImplementation());
            } else {
                Log.error("Only PackedOctree is supported.");
                return;
            }
        }

        if (camera == null) {
            camera = new ClCamera(manager.bufferedScene);
        }
        camera.generate();

        cl_mem buffer = clCreateBuffer(instance.context, CL_MEM_READ_WRITE | CL_MEM_COPY_HOST_PTR,
                (long) Sizeof.cl_float * passBuffer.length, Pointer.to(passBuffer), null);
        cl_kernel kernel = clCreateKernel(instance.program, "render", null);

        clSetKernelArg(kernel, 0, Sizeof.cl_mem, Pointer.to(camera.rayPos));
        clSetKernelArg(kernel, 1, Sizeof.cl_mem, Pointer.to(camera.rayDir));
        clSetKernelArg(kernel, 2, Sizeof.cl_mem, Pointer.to(octree.octreeDepth));
        clSetKernelArg(kernel, 3, Sizeof.cl_mem, Pointer.to(octree.octreeData));
        clSetKernelArg(kernel, 4, Sizeof.cl_mem, Pointer.to(buffer));
        clEnqueueNDRangeKernel(instance.commandQueue, kernel, 1, null,
                new long[] {passBuffer.length / 3}, null, 0, null, null);
        clEnqueueReadBuffer(instance.commandQueue, buffer, CL_TRUE, 0,
                (long) Sizeof.cl_float * passBuffer.length, Pointer.to(passBuffer),
                0, null, null);

        Chunky.getCommonThreads().submit(() -> Arrays.parallelSetAll(sampleBuffer, i -> passBuffer[i])).join();

        callback.getAsBoolean();
    }

    @Override
    public void sceneReset(DefaultRenderManager manager, ResetReason reason, int resetCount) {
        if (reason == ResetReason.SCENE_LOADED) {
            Octree.OctreeImplementation sceneOctree = manager.bufferedScene.getWorldOctree().getImplementation();
            if (sceneOctree instanceof PackedOctree)
                octree = new ClOctree((PackedOctree) sceneOctree);
        }

        if (reason == ResetReason.SETTINGS_CHANGED) {
            if (camera == null ||
                    manager.bufferedScene.width != camera.width ||
                    manager.bufferedScene.height != camera.height) {
                camera = new ClCamera(manager.bufferedScene);
            }
        }
    }

    @Override
    public boolean autoPostProcess() {
        return true;
    }
}
