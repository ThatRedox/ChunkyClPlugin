package dev.thatredox.chunkynative.opencl.renderer;

import dev.thatredox.chunkynative.opencl.context.ContextManager;
import dev.thatredox.chunkynative.opencl.renderer.ClSceneLoader;
import dev.thatredox.chunkynative.opencl.renderer.scene.*;
import dev.thatredox.chunkynative.opencl.util.ClIntBuffer;
import dev.thatredox.chunkynative.opencl.util.ClMemory;
import org.jocl.*;
import se.llbit.chunky.renderer.DefaultRenderManager;
import se.llbit.chunky.renderer.Renderer;
import se.llbit.chunky.renderer.ResetReason;
import se.llbit.chunky.renderer.scene.Scene;
import se.llbit.util.Mutable;

import java.util.function.BooleanSupplier;

import static org.jocl.CL.*;

public class OpenClPreviewRenderer implements Renderer {
    private BooleanSupplier postRender = () -> true;

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
        ContextManager context = ContextManager.get();
        ClSceneLoader sceneLoader = context.sceneLoader;

        cl_event[] renderEvent = new cl_event[1];
        Scene scene = manager.bufferedScene;
        int[] imageData = scene.getBackBuffer().data;

        // Ensure the scene is loaded
        sceneLoader.ensureLoad(manager.bufferedScene);

        // Load the kernel
        cl_kernel kernel = clCreateKernel(context.renderer.kernel, "preview", null);

        ClCamera camera = new ClCamera(scene, context.context);
        ClMemory buffer = new ClMemory(clCreateBuffer(context.context.context, CL_MEM_WRITE_ONLY,
                (long) Sizeof.cl_int * imageData.length, null, null));
        ClIntBuffer clWidth = new ClIntBuffer(scene.width, context.context);
        ClIntBuffer clHeight = new ClIntBuffer(scene.height, context.context);

        try (ClCamera ignored1 = camera;
             ClMemory ignored2 = buffer;
             ClIntBuffer ignored3 = clWidth;
             ClIntBuffer ignored4 = clHeight) {

            // Generate the camera rays
            camera.generate(null, false);

            renderEvent[0] = new cl_event();

            int argIndex = 0;
            clSetKernelArg(kernel, argIndex++, Sizeof.cl_mem, Pointer.to(camera.projectorType.get()));
            clSetKernelArg(kernel, argIndex++, Sizeof.cl_mem, Pointer.to(camera.cameraSettings.get()));

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

            clSetKernelArg(kernel, argIndex++, Sizeof.cl_mem, Pointer.to(sceneLoader.getSky().skyTexture.get()));
            clSetKernelArg(kernel, argIndex++, Sizeof.cl_mem, Pointer.to(sceneLoader.getSky().skyIntensity.get()));
            clSetKernelArg(kernel, argIndex++, Sizeof.cl_mem, Pointer.to(sceneLoader.getSun().get()));

            clSetKernelArg(kernel, argIndex++, Sizeof.cl_mem, Pointer.to(clWidth.get()));
            clSetKernelArg(kernel, argIndex++, Sizeof.cl_mem, Pointer.to(clHeight.get()));
            clSetKernelArg(kernel, argIndex++, Sizeof.cl_mem, Pointer.to(buffer.get()));
            clEnqueueNDRangeKernel(context.context.queue, kernel, 1, null,
                    new long[]{imageData.length}, null, 0, null,
                    renderEvent[0]);

            clEnqueueReadBuffer(context.context.queue, buffer.get(), CL_TRUE, 0,
                    (long) Sizeof.cl_int * imageData.length, Pointer.to(imageData),
                    1, renderEvent, null);

            manager.redrawScreen();
            postRender.getAsBoolean();
        }

        clReleaseKernel(kernel);
        clReleaseEvent(renderEvent[0]);
    }

    @Override
    public boolean autoPostProcess() {
        return false;
    }

    @Override
    public void sceneReset(DefaultRenderManager manager, ResetReason reason, int resetCount) {
        ContextManager.get().sceneLoader.load(resetCount, reason, manager.bufferedScene);
    }
}

