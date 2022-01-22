package chunkycl;

import static org.jocl.CL.*;

import chunkycl.renderer.RendererInstance;
import chunkycl.renderer.scene.*;
import org.jocl.*;

import se.llbit.chunky.entity.Entity;
import se.llbit.chunky.main.Chunky;
import se.llbit.chunky.renderer.*;
import se.llbit.chunky.renderer.scene.Scene;
import se.llbit.log.Log;
import se.llbit.math.Octree;
import se.llbit.math.PackedOctree;
import se.llbit.math.Vector3;
import se.llbit.math.bvh.BVH;
import se.llbit.math.bvh.BinaryBVH;
import se.llbit.math.bvh.SahMaBVH;
import se.llbit.math.primitive.Primitive;
import se.llbit.util.Mutable;
import se.llbit.util.TaskTracker;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Random;
import java.util.concurrent.ForkJoinTask;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.BooleanSupplier;

public class OpenClTestRenderer implements Renderer {

    private BooleanSupplier postRender = () -> true;

    private ClBvh clBvh;
    private ClMaterialPalette clMaterials;
    private ClTextureAtlas clAtlas;
    private ClBlockPalette clPalette;
    private ClOctree clOctree;
    private ClSky clSky;

    private float[] passBuffer;

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
    public void setPostRender(BooleanSupplier callback) {
        postRender = callback;
    }

    @Override
    public void render(DefaultRenderManager manager) throws InterruptedException {
        ReentrantLock renderLock = new ReentrantLock();
        cl_event[] renderEvent = new cl_event[1];
        Scene scene = manager.bufferedScene;

        RendererInstance instance = RendererInstance.get();
        double[] sampleBuffer = scene.getSampleBuffer();
        if (passBuffer == null || passBuffer.length != sampleBuffer.length) {
            passBuffer = new float[sampleBuffer.length];
        }

        if (clAtlas == null || clPalette == null || clOctree == null || clSky == null) {
            sceneReset(manager, null, 0);
        }

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

            clSetKernelArg(kernel, argIndex++, Sizeof.cl_mem, Pointer.to(clOctree.octreeDepth));
            clSetKernelArg(kernel, argIndex++, Sizeof.cl_mem, Pointer.to(clOctree.octreeData));

            clSetKernelArg(kernel, argIndex++, Sizeof.cl_mem, Pointer.to(clPalette.blocks));
            clSetKernelArg(kernel, argIndex++, Sizeof.cl_mem, Pointer.to(clPalette.quadsModels));
            clSetKernelArg(kernel, argIndex++, Sizeof.cl_mem, Pointer.to(clPalette.aabbModels));

            clSetKernelArg(kernel, argIndex++, Sizeof.cl_mem, Pointer.to(clBvh.bvh));
            clSetKernelArg(kernel, argIndex++, Sizeof.cl_mem, Pointer.to(clBvh.trigs));

            clSetKernelArg(kernel, argIndex++, Sizeof.cl_mem, Pointer.to(clAtlas.texture));
            clSetKernelArg(kernel, argIndex++, Sizeof.cl_mem, Pointer.to(clMaterials.materials));

            clSetKernelArg(kernel, argIndex++, Sizeof.cl_mem, Pointer.to(clSky.skyTexture));
            clSetKernelArg(kernel, argIndex++, Sizeof.cl_mem, Pointer.to(clSky.sunIntensity));

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

    public void sceneReset(DefaultRenderManager manager, ResetReason reason, int resetCount) {
        if (reason == null || reason == ResetReason.SCENE_LOADED || reason == ResetReason.MATERIALS_CHANGED) {
            if (clAtlas != null) clAtlas.release();
            if (clPalette != null) clPalette.release();
            if (clMaterials != null) clMaterials.release();
            if (clOctree != null) clOctree.release();
            if (clBvh != null) clBvh.release();

            Octree.OctreeImplementation sceneOctree = manager.bufferedScene.getWorldOctree().getImplementation();
            if (sceneOctree instanceof PackedOctree) {
                clOctree = new ClOctree((PackedOctree) sceneOctree);
            } else {
                Log.error("Only PackedOctree is supported.");
                return;
            }

            BinaryBVH bvh = buildBvh(manager.bufferedScene);

            ClMaterialPalette.Builder materialBuilder = new ClMaterialPalette.Builder();
            ClTextureAtlas.AtlasBuilder atlasBuilder = new ClTextureAtlas.AtlasBuilder();

            ClBlockPalette.preLoad(manager.bufferedScene.getPalette(), atlasBuilder);
            ClBvh.preload(bvh, atlasBuilder);

            clAtlas = atlasBuilder.build();
            atlasBuilder = null;

            clPalette = new ClBlockPalette(manager.bufferedScene.getPalette(), clAtlas, materialBuilder);
            clBvh = new ClBvh(bvh, clAtlas, materialBuilder);

            clMaterials = materialBuilder.build();
            materialBuilder = null;
        }

        if (reason == null || reason.overwriteState()) {
            if (clSky != null) clSky.release();
            clSky = new ClSky(manager.bufferedScene);
        }
    }

    private static BinaryBVH buildBvh(Scene scene) {
        ArrayList<Entity> entities = new ArrayList<>();
        entities.addAll(scene.getEntities());
        entities.addAll(scene.getActors());
        Vector3 worldOffset = new Vector3();
        worldOffset.x = -scene.getOrigin().x;
        worldOffset.y = -scene.getOrigin().y;
        worldOffset.z = -scene.getOrigin().z;
        return (BinaryBVH) BVH.Factory.create("SAH_MA", entities, worldOffset, TaskTracker.Task.NONE);
    }
}
