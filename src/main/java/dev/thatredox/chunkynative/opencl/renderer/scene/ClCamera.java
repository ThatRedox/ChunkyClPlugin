package dev.thatredox.chunkynative.opencl.renderer.scene;

import dev.thatredox.chunkynative.opencl.renderer.RendererInstance;
import dev.thatredox.chunkynative.opencl.util.ClMemory;
import dev.thatredox.chunkynative.util.Reflection;
import dev.thatredox.chunkynative.util.Util;
import it.unimi.dsi.fastutil.floats.FloatArrayList;
import it.unimi.dsi.fastutil.floats.FloatList;
import org.jocl.Pointer;
import org.jocl.Sizeof;
import se.llbit.chunky.main.Chunky;
import se.llbit.chunky.renderer.scene.Camera;
import se.llbit.chunky.renderer.scene.Scene;
import se.llbit.math.Matrix3;
import se.llbit.math.Ray;
import se.llbit.math.Vector3;

import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.locks.Lock;
import java.util.stream.IntStream;

import static org.jocl.CL.*;

public class ClCamera implements AutoCloseable {
    public ClMemory projectorType;
    public ClMemory cameraSettings;
    public final boolean needGenerate;

    private final Scene scene;


    public ClCamera(Scene scene) {
        RendererInstance instance = RendererInstance.get();
        this.scene = scene;
        Camera camera = scene.camera();

        int projType = -1;
        Vector3 pos = new Vector3(camera.getPosition());
        pos.sub(scene.getOrigin());

        FloatArrayList settings = new FloatArrayList();
        settings.addAll(FloatList.of(Util.vector3ToFloat(pos)));
        settings.addAll(FloatList.of(Util.matrix3ToFloat(Reflection.getFieldValue(camera, "transform", Matrix3.class))));

        switch (camera.getProjectionMode()) {
            case PINHOLE:
                projType = 0;
                settings.add(camera.infiniteDoF() ? 0 : (float) (camera.getSubjectDistance() / camera.getDof()));
                settings.add((float) camera.getSubjectDistance());
                settings.add((float) Camera.clampedFovTan(camera.getFov()));
                break;
            default:
                // We need to pre-generate rays
                break;
        }

        needGenerate = projType == -1;

        projectorType = new ClMemory(clCreateBuffer(instance.context, CL_MEM_READ_ONLY | CL_MEM_COPY_HOST_PTR,
                Sizeof.cl_int, Pointer.to(new int[] {projType}), null));

        if (needGenerate) {
            cameraSettings = new ClMemory(clCreateBuffer(instance.context, CL_MEM_READ_ONLY,
                    (long) Sizeof.cl_float * scene.width * scene.height * 3 * 2, null, null));
        } else {
            cameraSettings = new ClMemory(clCreateBuffer(instance.context, CL_MEM_READ_ONLY | CL_MEM_COPY_HOST_PTR,
                    (long) Sizeof.cl_float * settings.size(), Pointer.to(settings.toFloatArray()), null));
        }
    }

    public void generate(Lock renderLock, boolean jitter) {
        if (!needGenerate) return;

        float[] rays = new float[scene.width * scene.height * 3 * 2];

        double halfWidth = scene.width / (2.0 * scene.height);
        double invHeight = 1.0 / scene.height;

        Camera cam = scene.camera();

        Chunky.getCommonThreads().submit(() -> IntStream.range(0, scene.width).parallel().forEach(i -> {
            Ray ray = new Ray();
            Random random = jitter ? ThreadLocalRandom.current() : null;
            for (int j = 0; j < scene.height; j++) {
                int offset = (j * scene.width + i) * 3 * 2;

                float ox = jitter ? random.nextFloat(): 0.5f;
                float oy = jitter ? random.nextFloat(): 0.5f;

                cam.calcViewRay(ray, -halfWidth + (i + ox) * invHeight, -0.5 + (j + oy) * invHeight);
                ray.o.sub(scene.getOrigin());

                System.arraycopy(Util.vector3ToFloat(ray.o), 0, rays, offset, 3);
                System.arraycopy(Util.vector3ToFloat(ray.d), 0, rays, offset+3, 3);
            }
        })).join();

        if (renderLock != null) renderLock.lock();
        RendererInstance instance = RendererInstance.get();
        clEnqueueWriteBuffer(instance.commandQueue, this.cameraSettings.get(), CL_TRUE, 0,
                (long) Sizeof.cl_float * rays.length, Pointer.to(rays), 0,
                null, null);
        if (renderLock != null) renderLock.unlock();
    }

    @Override
    public void close() {
        this.projectorType.close();
        this.cameraSettings.close();
    }
}
