package dev.thatredox.chunkynative.opencl;

import static org.jocl.CL.*;

import dev.thatredox.chunkynative.opencl.renderer.ClSceneLoader;
import dev.thatredox.chunkynative.opencl.renderer.RendererInstance;
import dev.thatredox.chunkynative.opencl.renderer.scene.*;
import org.jocl.*;

import se.llbit.chunky.entity.Entity;
import se.llbit.chunky.main.Chunky;
import se.llbit.chunky.renderer.*;
import se.llbit.chunky.renderer.scene.Scene;
import se.llbit.math.Vector3;
import se.llbit.math.bvh.BVH;
import se.llbit.math.bvh.BinaryBVH;
import se.llbit.util.TaskTracker;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Random;
import java.util.concurrent.ForkJoinTask;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.BooleanSupplier;

public class OpenClPathTracingRenderer implements Renderer {

    private BooleanSupplier postRender = () -> true;

    private final ClSceneLoader sceneLoader;

    public OpenClPathTracingRenderer(ClSceneLoader sceneLoader) {
        this.sceneLoader = sceneLoader;
    }

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
        RendererInstance instance = RendererInstance.get();
        ReentrantLock renderLock = new ReentrantLock();
        cl_event[] renderEvent = new cl_event[1];
        Scene scene = manager.bufferedScene;

        double[] sampleBuffer = scene.getSampleBuffer();
        float[] passBuffer = new float[sampleBuffer.length];

        // Ensure the scene is loaded
        sceneLoader.ensureLoad(manager.bufferedScene);

        // Generate the camera
        ClCamera camera = new ClCamera(scene);
        camera.generate(renderLock);

        cl_kernel kernel = clCreateKernel(instance.program, "render", null);

        cl_mem buffer = clCreateBuffer(instance.context, CL_MEM_READ_WRITE | CL_MEM_COPY_HOST_PTR,
                (long) Sizeof.cl_float * passBuffer.length, Pointer.to(passBuffer), null);
        cl_mem randomSeed = clCreateBuffer(instance.context, CL_MEM_READ_ONLY, Sizeof.cl_int, null, null);
        cl_mem bufferSpp = clCreateBuffer(instance.context, CL_MEM_READ_ONLY, Sizeof.cl_int, null, null);

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

            clEnqueueWriteBuffer(instance.commandQueue, randomSeed, CL_TRUE, 0, Sizeof.cl_int,
                    Pointer.to(new int[] {rand.nextInt()}), 0, null, null);
            clEnqueueWriteBuffer(instance.commandQueue, bufferSpp, CL_TRUE, 0, Sizeof.cl_int,
                    Pointer.to(new int[] {bufferSppReal}), 0, null, null);

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

            clSetKernelArg(kernel, argIndex++, Sizeof.cl_mem, Pointer.to(randomSeed));
            clSetKernelArg(kernel, argIndex++, Sizeof.cl_mem, Pointer.to(bufferSpp));
            clSetKernelArg(kernel, argIndex++, Sizeof.cl_mem, Pointer.to(buffer));
            clEnqueueNDRangeKernel(instance.commandQueue, kernel, 1, null,
                    new long[] {passBuffer.length / 3}, null, 0, null,
                    renderEvent[0]);
            clWaitForEvents(1, renderEvent);
            renderLock.unlock();
            bufferSppReal += 1;
            scene.spp += 1;

            if (cameraGenTask.isDone()) {
                cameraGenTask = Chunky.getCommonThreads().submit(() -> camera.generate(renderLock));
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
                clEnqueueReadBuffer(instance.commandQueue, buffer, CL_TRUE, 0,
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
        camera.release();
        clReleaseMemObject(buffer);
        clReleaseMemObject(randomSeed);
        clReleaseMemObject(bufferSpp);
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
        sceneLoader.load(resetCount, reason, manager.bufferedScene);
    }
}
