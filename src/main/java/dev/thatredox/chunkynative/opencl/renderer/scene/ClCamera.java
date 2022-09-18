package dev.thatredox.chunkynative.opencl.renderer.scene;

import static org.jocl.CL.*;

import dev.thatredox.chunkynative.opencl.renderer.RendererInstance;
import dev.thatredox.chunkynative.opencl.util.ClMemory;
import org.jocl.*;

import se.llbit.chunky.main.Chunky;
import se.llbit.chunky.renderer.scene.Camera;
import se.llbit.chunky.renderer.scene.Scene;
import se.llbit.math.Ray;

import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.locks.Lock;
import java.util.stream.IntStream;

public class ClCamera implements AutoCloseable {
    public ClMemory rayPos;
    public ClMemory rayDir;

    public final int width;
    public final int height;
    private final Scene scene;

    public ClCamera(Scene scene) {
        this.scene = scene;

        RendererInstance instance = RendererInstance.get();
        long bufferSize = (long) Sizeof.cl_float * scene.width * scene.height * 3;

        width = scene.width;
        height = scene.height;

        rayPos = new ClMemory(clCreateBuffer(instance.context, CL_MEM_READ_ONLY,
                bufferSize, null, null));
        rayDir = new ClMemory(clCreateBuffer(instance.context, CL_MEM_READ_ONLY,
                bufferSize, null, null));
    }

    public void generate(Lock renderLock, boolean jitter) {
        float[] rayDirs = new float[width * height * 3];
        float[] rayPos = new float[width * height * 3];

        double halfWidth = width / (2.0 * height);
        double invHeight = 1.0 / height;

        Camera cam = scene.camera();

        Chunky.getCommonThreads().submit(() -> IntStream.range(0, width).parallel().forEach(i -> {
            Ray ray = new Ray();
            Random random = jitter ? ThreadLocalRandom.current() : null;
            for (int j = 0; j < height; j++) {
                int offset = (j * width + i) * 3;

                float ox = jitter ? random.nextFloat(): 0.5f;
                float oy = jitter ? random.nextFloat(): 0.5f;

                cam.calcViewRay(ray, -halfWidth + (i + ox) * invHeight, -0.5 + (j + oy) * invHeight);

                rayDirs[offset + 0] = (float) ray.d.x;
                rayDirs[offset + 1] = (float) ray.d.y;
                rayDirs[offset + 2] = (float) ray.d.z;

                rayPos[offset + 0] = (float) (ray.o.x - scene.getOrigin().x);
                rayPos[offset + 1] = (float) (ray.o.y - scene.getOrigin().y);
                rayPos[offset + 2] = (float) (ray.o.z - scene.getOrigin().z);
            }
        })).join();

        if (renderLock != null) renderLock.lock();
        RendererInstance instance = RendererInstance.get();
        clEnqueueWriteBuffer(instance.commandQueue, this.rayPos.get(), CL_TRUE, 0,
                (long) Sizeof.cl_float * rayPos.length, Pointer.to(rayPos), 0,
                null, null);
        clEnqueueWriteBuffer(instance.commandQueue, this.rayDir.get(), CL_TRUE, 0,
                (long) Sizeof.cl_float * rayDirs.length, Pointer.to(rayDirs), 0,
                null, null);
        if (renderLock != null) renderLock.unlock();
    }

    @Override
    public void close() {
        this.rayDir.close();
        this.rayPos.close();
    }
}
