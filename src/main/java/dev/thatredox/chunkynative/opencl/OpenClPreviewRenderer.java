package dev.thatredox.chunkynative.opencl;

import dev.thatredox.chunkynative.opencl.renderer.ClSceneLoader;
import dev.thatredox.chunkynative.opencl.renderer.RendererInstance;
import dev.thatredox.chunkynative.opencl.renderer.scene.*;
import dev.thatredox.chunkynative.opencl.util.ClBuffer;
import org.jocl.*;
import se.llbit.chunky.renderer.DefaultRenderManager;
import se.llbit.chunky.renderer.Renderer;
import se.llbit.chunky.renderer.ResetReason;
import se.llbit.chunky.renderer.scene.Scene;
import java.util.function.BooleanSupplier;

import static org.jocl.CL.*;
import static org.jocl.CL.clReleaseMemObject;

public class OpenClPreviewRenderer implements Renderer {
    private BooleanSupplier postRender = () -> true;

    private final ClSceneLoader sceneLoader;

    public OpenClPreviewRenderer(ClSceneLoader sceneLoader) {
        this.sceneLoader = sceneLoader;
    }

    @Override
    public String getId() {
        return "ChunkyClPreviewRenderer";
    }

    @Override
    public String getName() {
        return "Chunky CL Preview Renderer";
    }

    @Override
    public String getDescription() {
        return "A work in progress OpenCL renderer.";
    }

    @Override
    public void setPostRender(BooleanSupplier callback) {
        postRender = callback;
    }

    @Override
    public void render(DefaultRenderManager manager) throws InterruptedException {
        cl_event[] renderEvent = new cl_event[1];
        Scene scene = manager.bufferedScene;

        RendererInstance instance = RendererInstance.get();
        int[] imageData = scene.getBackBuffer().data;

        // Ensure the scene is loaded
        sceneLoader.ensureLoad(manager.bufferedScene);

        ClCamera camera = new ClCamera(scene);
        camera.generateNoJitter();

        cl_kernel kernel = clCreateKernel(instance.program, "preview", null);
        cl_mem buffer = clCreateBuffer(instance.context, CL_MEM_WRITE_ONLY,
                (long) Sizeof.cl_int * imageData.length, null, null);

        ClBuffer clWidth = ClBuffer.singletonBuffer(scene.width);
        ClBuffer clHeight = ClBuffer.singletonBuffer(scene.height);

        renderEvent[0] = new cl_event();

        int argIndex = 0;
        clSetKernelArg(kernel, argIndex++, Sizeof.cl_mem, Pointer.to(camera.rayPos));
        clSetKernelArg(kernel, argIndex++, Sizeof.cl_mem, Pointer.to(camera.rayDir));

        clSetKernelArg(kernel, argIndex++, Sizeof.cl_mem, Pointer.to(sceneLoader.getOctreeDepth().get()));
        clSetKernelArg(kernel, argIndex++, Sizeof.cl_mem, Pointer.to(sceneLoader.getOctreeData().get()));

        clSetKernelArg(kernel, argIndex++, Sizeof.cl_mem, Pointer.to(sceneLoader.getBlockPalette().get()));
        clSetKernelArg(kernel, argIndex++, Sizeof.cl_mem, Pointer.to(sceneLoader.getQuadPalette().get()));
        clSetKernelArg(kernel, argIndex++, Sizeof.cl_mem, Pointer.to(sceneLoader.getAabbPalette().get()));

        clSetKernelArg(kernel, argIndex++, Sizeof.cl_mem, Pointer.to(sceneLoader.getWorldBvh().get()));
        clSetKernelArg(kernel, argIndex++, Sizeof.cl_mem, Pointer.to(sceneLoader.getActorBvh().get()));
        clSetKernelArg(kernel, argIndex++, Sizeof.cl_mem, Pointer.to(sceneLoader.getTrigPalette().get()));

        clSetKernelArg(kernel, argIndex++, Sizeof.cl_mem, Pointer.to(sceneLoader.getTexturePalette().getAtlas()));
        clSetKernelArg(kernel, argIndex++, Sizeof.cl_mem, Pointer.to(sceneLoader.getMaterialPalette().get()));

        clSetKernelArg(kernel, argIndex++, Sizeof.cl_mem, Pointer.to(sceneLoader.getSky().skyTexture));
        clSetKernelArg(kernel, argIndex++, Sizeof.cl_mem, Pointer.to(sceneLoader.getSky().skyIntensity));
        clSetKernelArg(kernel, argIndex++, Sizeof.cl_mem, Pointer.to(sceneLoader.getSun().get()));

        clSetKernelArg(kernel, argIndex++, Sizeof.cl_mem, Pointer.to(clWidth.get()));
        clSetKernelArg(kernel, argIndex++, Sizeof.cl_mem, Pointer.to(clHeight.get()));
        clSetKernelArg(kernel, argIndex++, Sizeof.cl_mem, Pointer.to(buffer));
        clEnqueueNDRangeKernel(instance.commandQueue, kernel, 1, null,
                new long[] {imageData.length}, null, 0, null,
                renderEvent[0]);

        clEnqueueReadBuffer(instance.commandQueue, buffer, CL_TRUE, 0,
                (long) Sizeof.cl_int * imageData.length, Pointer.to(imageData),
                1, renderEvent, null);

        manager.redrawScreen();
        postRender.getAsBoolean();

        camera.release();
        clReleaseMemObject(buffer);
        clWidth.release();
        clHeight.release();
        clReleaseKernel(kernel);
        clReleaseEvent(renderEvent[0]);
    }

    @Override
    public boolean autoPostProcess() {
        return false;
    }

    @Override
    public void sceneReset(DefaultRenderManager manager, ResetReason reason, int resetCount) {
        sceneLoader.load(resetCount, reason, manager.bufferedScene);
    }
}

