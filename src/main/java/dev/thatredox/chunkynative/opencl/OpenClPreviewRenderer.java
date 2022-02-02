package dev.thatredox.chunkynative.opencl;

import dev.thatredox.chunkynative.opencl.export.ClTextureLoader;
import dev.thatredox.chunkynative.opencl.renderer.RendererInstance;
import dev.thatredox.chunkynative.opencl.renderer.scene.*;
import dev.thatredox.chunkynative.opencl.renderer.scene.primitives.ClSun;
import dev.thatredox.chunkynative.opencl.util.ClBuffer;
import org.jocl.*;
import se.llbit.chunky.entity.Entity;
import se.llbit.chunky.renderer.DefaultRenderManager;
import se.llbit.chunky.renderer.Renderer;
import se.llbit.chunky.renderer.ResetReason;
import se.llbit.chunky.renderer.scene.Scene;
import se.llbit.chunky.renderer.scene.Sun;
import se.llbit.log.Log;
import se.llbit.math.Octree;
import se.llbit.math.PackedOctree;
import se.llbit.math.Vector3;
import se.llbit.math.bvh.BVH;
import se.llbit.math.bvh.BinaryBVH;
import se.llbit.util.TaskTracker;

import java.util.ArrayList;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.BooleanSupplier;

import static org.jocl.CL.*;
import static org.jocl.CL.clReleaseMemObject;

public class OpenClPreviewRenderer implements Renderer {
    private BooleanSupplier postRender = () -> true;

    private ClBvh clBvh;
    private ClMaterialPalette clMaterials;
    private ClTextureLoader clAtlas;
    private ClBlockPalette clPalette;
    private ClOctree clOctree;
    private ClSky clSky;

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
        ReentrantLock renderLock = new ReentrantLock();
        cl_event[] renderEvent = new cl_event[1];
        Scene scene = manager.bufferedScene;

        RendererInstance instance = RendererInstance.get();
        int[] imageData = scene.getBackBuffer().data;

        if (clAtlas == null || clPalette == null || clOctree == null || clSky == null) {
            sceneReset(manager, null, 0);
        }

        ClCamera camera = new ClCamera(scene);
        camera.generate(renderLock);

        ClSun sun = new ClSun(scene.sun(), this.clAtlas);
        ClBuffer sunBuffer = new ClBuffer(sun.pack());

        cl_kernel kernel = clCreateKernel(instance.program, "preview", null);
        cl_mem randomSeed = clCreateBuffer(instance.context, CL_MEM_READ_ONLY, Sizeof.cl_int, null, null);
        cl_mem buffer = clCreateBuffer(instance.context, CL_MEM_WRITE_ONLY,
                (long) Sizeof.cl_int * imageData.length, null, null);
        ClBuffer clWidth = ClBuffer.singletonBuffer(scene.width);
        ClBuffer clHeight = ClBuffer.singletonBuffer(scene.height);

        renderEvent[0] = new cl_event();
        clEnqueueWriteBuffer(instance.commandQueue, randomSeed, CL_TRUE, 0, Sizeof.cl_int,
                Pointer.to(new int[] {0}), 0, null, null);

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

        clSetKernelArg(kernel, argIndex++, Sizeof.cl_mem, Pointer.to(clAtlas.getAtlas()));
        clSetKernelArg(kernel, argIndex++, Sizeof.cl_mem, Pointer.to(clMaterials.materials));

        clSetKernelArg(kernel, argIndex++, Sizeof.cl_mem, Pointer.to(clSky.skyTexture));
        clSetKernelArg(kernel, argIndex++, Sizeof.cl_mem, Pointer.to(clSky.sunIntensity));
        clSetKernelArg(kernel, argIndex++, Sizeof.cl_mem, Pointer.to(sunBuffer.get()));

        clSetKernelArg(kernel, argIndex++, Sizeof.cl_mem, Pointer.to(randomSeed));
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
        clReleaseMemObject(randomSeed);
        clReleaseMemObject(buffer);
        clWidth.release();
        clHeight.release();
        sunBuffer.release();
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
            clAtlas = new ClTextureLoader();

            clAtlas.get(Sun.texture);
            ClBlockPalette.preLoad(manager.bufferedScene.getPalette(), clAtlas);
            ClBvh.preload(bvh, clAtlas);

            clAtlas.build();

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
        return (BinaryBVH) BVH.Factory.getImplementation("SAH_MA").create(entities, worldOffset, TaskTracker.Task.NONE);
    }
}

