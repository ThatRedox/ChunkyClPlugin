package chunkycl.renderer.scene;

import static org.jocl.CL.*;

import chunkycl.renderer.RendererInstance;
import org.jocl.*;

import se.llbit.chunky.main.Chunky;
import se.llbit.chunky.renderer.scene.Camera;
import se.llbit.chunky.renderer.scene.Scene;
import se.llbit.math.Ray;

import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.IntStream;

public class ClCamera {
    public cl_mem rayPos;
    public cl_mem rayDir;

    public final int width;
    public final int height;
    private final Scene scene;

    public ClCamera(Scene scene) {
        this.scene = scene;

        RendererInstance instance = RendererInstance.get();
        long bufferSize = (long) Sizeof.cl_float * scene.width * scene.height * 3;

        width = scene.width;
        height = scene.height;

        rayPos = clCreateBuffer(instance.context, CL_MEM_READ_ONLY,
                bufferSize, null, null);
        rayDir = clCreateBuffer(instance.context, CL_MEM_READ_ONLY,
                bufferSize, null, null);
    }

    public void generate() {
        float[] rayDirs = new float[width * height * 3];
        float[] rayPos = new float[width * height * 3];

        double halfWidth = width / (2.0 * height);
        double invHeight = 1.0 / height;

        Camera cam = scene.camera();

        Chunky.getCommonThreads().submit(() -> IntStream.range(0, width).parallel().forEach(i -> {
            Ray ray = new Ray();
            Random random = ThreadLocalRandom.current();
            for (int j = 0; j < height; j++) {
                int offset = (j * width + i) * 3;

                float ox = random.nextFloat();
                float oy = random.nextFloat();

                cam.calcViewRay(ray, -halfWidth + (i + ox) * invHeight, -0.5 + (j + oy) * invHeight);

                rayDirs[offset + 0] = (float) ray.d.x;
                rayDirs[offset + 1] = (float) ray.d.y;
                rayDirs[offset + 2] = (float) ray.d.z;

                rayPos[offset + 0] = (float) (ray.o.x - scene.getOrigin().x);
                rayPos[offset + 1] = (float) (ray.o.y - scene.getOrigin().y);
                rayPos[offset + 2] = (float) (ray.o.z - scene.getOrigin().z);
            }
        })).join();

        RendererInstance instance = RendererInstance.get();
        clEnqueueWriteBuffer(instance.commandQueue, this.rayPos, CL_TRUE, 0,
                (long) Sizeof.cl_float * rayPos.length, Pointer.to(rayPos), 0,
                null, null);
        clEnqueueWriteBuffer(instance.commandQueue, this.rayDir, CL_TRUE, 0,
                (long) Sizeof.cl_float * rayDirs.length, Pointer.to(rayDirs), 0,
                null, null);
    }

    public void generateNoJitter() {
        float[] rayDirs = new float[width * height * 3];
        float[] rayPos = new float[width * height * 3];

        double halfWidth = width / (2.0 * height);
        double invHeight = 1.0 / height;

        Camera cam = scene.camera();

        Chunky.getCommonThreads().submit(() -> IntStream.range(0, width).parallel().forEach(i -> {
            Ray ray = new Ray();
            for (int j = 0; j < height; j++) {
                int offset = (j * width + i) * 3;
                cam.calcViewRay(ray, -halfWidth + i * invHeight, -0.5 + j*invHeight);

                rayDirs[offset + 0] = (float) ray.d.x;
                rayDirs[offset + 1] = (float) ray.d.y;
                rayDirs[offset + 2] = (float) ray.d.z;

                rayPos[offset + 0] = (float) ray.o.x;
                rayPos[offset + 1] = (float) ray.o.x;
                rayPos[offset + 2] = (float) ray.o.x;
            }
        })).join();

        RendererInstance instance = RendererInstance.get();
        clEnqueueWriteBuffer(instance.commandQueue, this.rayPos, CL_TRUE, 0,
                (long) Sizeof.cl_float * rayPos.length, Pointer.to(rayPos), 0,
                null, null);
        clEnqueueWriteBuffer(instance.commandQueue, this.rayDir, CL_TRUE, 0,
                (long) Sizeof.cl_float * rayDirs.length, Pointer.to(rayDirs), 0,
                null, null);
    }

    public void release() {
        clReleaseMemObject(rayPos);
        clReleaseMemObject(rayDir);
    }
}
