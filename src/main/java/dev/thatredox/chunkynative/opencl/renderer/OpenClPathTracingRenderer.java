package dev.thatredox.chunkynative.opencl.renderer;

import static org.jocl.CL.*;

import dev.thatredox.chunkynative.opencl.context.ContextManager;
import dev.thatredox.chunkynative.opencl.renderer.ClSceneLoader;
import dev.thatredox.chunkynative.opencl.renderer.scene.*;
import dev.thatredox.chunkynative.opencl.util.ClIntBuffer;
import dev.thatredox.chunkynative.opencl.util.ClMemory;
import org.jocl.*;

import se.llbit.chunky.main.Chunky;
import se.llbit.chunky.renderer.*;
import se.llbit.chunky.renderer.scene.Scene;
import se.llbit.util.Mutable;
import se.llbit.util.TaskTracker;

import java.util.Arrays;
import java.util.Random;
import java.util.concurrent.ForkJoinTask;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.BooleanSupplier;

public class OpenClPathTracingRenderer implements Renderer {

    private BooleanSupplier postRender = () -> true;

    @Override
    public String getId() {
        return "ChunkyClRenderer";
    }

    @Override
    public String getName() {
        return "ChunkyClRenderer";
    }

    @Override
    public String getDescription() {
        return "ChunkyClRenderer";
    }

    @Override
    public void setPostRender(BooleanSupplier callback) {
        postRender = callback;
    }

    @Override
    public void render(DefaultRenderManager manager) throws InterruptedException {
        ContextManager context = ContextManager.get();
        ClSceneLoader sceneLoader = context.sceneLoader;

        ReentrantLock renderLock = new ReentrantLock();
        cl_event[] renderEvent = new cl_event[1];
        Scene scene = manager.bufferedScene;

        double[] sampleBuffer = scene.getSampleBuffer();
        float[] passBuffer = new float[sampleBuffer.length];

        // Ensure the scene is loaded
        sceneLoader.ensureLoad(manager.bufferedScene);

        // Load the kernel
        cl_kernel kernel = clCreateKernel(context.renderer.kernel, "render", null);

        // Generate the camera
        ClCamera camera = new ClCamera(scene, context.context);
        ClMemory buffer = new ClMemory(clCreateBuffer(context.context.context, CL_MEM_READ_WRITE | CL_MEM_COPY_HOST_PTR,
                (long) Sizeof.cl_float * passBuffer.length, Pointer.to(passBuffer), null));
        ClMemory randomSeed = new ClMemory(
                clCreateBuffer(context.context.context, CL_MEM_READ_ONLY, Sizeof.cl_int, null, null));
        ClMemory bufferSpp = new ClMemory(
                clCreateBuffer(context.context.context, CL_MEM_READ_ONLY, Sizeof.cl_int, null, null));
        ClIntBuffer clWidth = new ClIntBuffer(scene.canvasConfig.getWidth(), context.context);
        ClIntBuffer clHeight = new ClIntBuffer(scene.canvasConfig.getHeight(), context.context);

        try (ClCamera ignored1 = camera;
             ClMemory ignored2 = buffer;
             ClMemory ignored3 = randomSeed;
             ClMemory ignored4 = bufferSpp;
             ClIntBuffer ignored5 = clWidth;
             ClIntBuffer ignored6 = clHeight) {

            // Generate initial camera rays
            camera.generate(renderLock, true);

            int bufferSppReal = 0;
            int logicalSpp = scene.spp;
            final int[] sceneSpp = {scene.spp};
            long lastCallback = 0;

            Random rand = new Random(0);

            ForkJoinTask<?> cameraGenTask = Chunky.getCommonThreads().submit(() -> 0);
            ForkJoinTask<?> bufferMergeTask = Chunky.getCommonThreads().submit(() -> 0);

            // This is the main rendering loop. This deals with dispatching rendering tasks. The majority of time is spent
            // waiting for the OpenCL renderer to complete.
            while (logicalSpp < scene.getTargetSpp()) {
                renderLock.lock();
                renderEvent[0] = new cl_event();

                clEnqueueWriteBuffer(context.context.queue, randomSeed.get(), CL_TRUE, 0, Sizeof.cl_int,
                        Pointer.to(new int[]{rand.nextInt()}), 0, null, null);
                clEnqueueWriteBuffer(context.context.queue, bufferSpp.get(), CL_TRUE, 0, Sizeof.cl_int,
                        Pointer.to(new int[]{bufferSppReal}), 0, null, null);

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

                clSetKernelArg(kernel, argIndex++, Sizeof.cl_mem, Pointer.to(randomSeed.get()));
                clSetKernelArg(kernel, argIndex++, Sizeof.cl_mem, Pointer.to(bufferSpp.get()));
                clSetKernelArg(kernel, argIndex++, Sizeof.cl_mem, Pointer.to(clWidth.get()));
                clSetKernelArg(kernel, argIndex++, Sizeof.cl_mem, Pointer.to(clHeight.get()));
                clSetKernelArg(kernel, argIndex++, Sizeof.cl_mem, Pointer.to(buffer.get()));
                clEnqueueNDRangeKernel(context.context.queue, kernel, 1, null,
                        new long[]{passBuffer.length / 3}, null, 0, null,
                        renderEvent[0]);
                clWaitForEvents(1, renderEvent);
                renderLock.unlock();
                bufferSppReal += 1;
                scene.spp += 1;

                if (camera.needGenerate && cameraGenTask.isDone()) {
                    cameraGenTask = Chunky.getCommonThreads().submit(() -> camera.generate(renderLock, true));
                }

                boolean saveEvent = isSaveEvent(manager.getSnapshotControl(), scene, logicalSpp + bufferSppReal);
                if (bufferMergeTask.isDone() || saveEvent) {
                    if (!scene.shouldFinalizeBuffer() && !saveEvent) {
                        long time = System.currentTimeMillis();
                        if (time - lastCallback > 100 && !manager.shouldFinalize()) {
                            lastCallback = time;
                            if (postRender.getAsBoolean()) break;
                        }
                        if (bufferSppReal < 1024)
                            continue;
                    }

                    bufferMergeTask.join();
                    if (postRender.getAsBoolean()) break;
                    clEnqueueReadBuffer(context.context.queue, buffer.get(), CL_TRUE, 0,
                            (long) Sizeof.cl_float * passBuffer.length, Pointer.to(passBuffer),
                            0, null, null);
                    int sampSpp = sceneSpp[0];
                    int passSpp = bufferSppReal;
                    double sinv = 1.0 / (sampSpp + passSpp);
                    bufferSppReal = 0;

                    bufferMergeTask = Chunky.getCommonThreads().submit(() -> {
                        Arrays.parallelSetAll(sampleBuffer, i -> (sampleBuffer[i] * sampSpp + passBuffer[i] * passSpp) * sinv);
                        sceneSpp[0] += passSpp;
                        scene.postProcessFrame(TaskTracker.Task.NONE);
                        manager.redrawScreen();
                    });
                    logicalSpp += passSpp;
                    if (saveEvent) {
                        bufferMergeTask.join();
                        if (postRender.getAsBoolean()) break;
                    }
                }
            }

            cameraGenTask.join();
            bufferMergeTask.join();
        }

        clReleaseKernel(kernel);
    }

    private boolean isSaveEvent(SnapshotControl control, Scene scene, int spp) {
        return control.saveSnapshot(scene, spp) || control.saveRenderDump(scene, spp);
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
