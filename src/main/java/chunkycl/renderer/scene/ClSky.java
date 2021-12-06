package chunkycl.renderer.scene;


import static org.jocl.CL.*;

import chunkycl.renderer.RendererInstance;
import org.apache.commons.math3.util.FastMath;
import org.jocl.*;

import se.llbit.chunky.main.Chunky;
import se.llbit.chunky.renderer.scene.Scene;
import se.llbit.chunky.renderer.scene.Sky;
import se.llbit.chunky.renderer.scene.SkyCache;
import se.llbit.log.Log;
import se.llbit.math.Ray;

import java.lang.reflect.Field;
import java.util.stream.IntStream;

public class ClSky {
    public final cl_mem skyTexture;
    public final cl_mem sunIntensity;

    private final int textureResolution;

    public ClSky(Scene scene) {
        this.textureResolution = getTextureResolution(scene);

        RendererInstance instance = RendererInstance.get();

        sunIntensity = clCreateBuffer(instance.context,
                CL_MEM_READ_ONLY | CL_MEM_COPY_HOST_PTR, Sizeof.cl_float,
                Pointer.to(new float[] {(float) scene.sun().getIntensity()}), null);

        cl_image_format fmt = new cl_image_format();
        fmt.image_channel_data_type = CL_UNORM_INT8;
        fmt.image_channel_order = CL_RGBA;

        cl_image_desc desc = new cl_image_desc();
        desc.image_type = CL_MEM_OBJECT_IMAGE2D;
        desc.image_width = textureResolution + 1;
        desc.image_height = textureResolution + 1;

        skyTexture = clCreateImage(instance.context, CL_MEM_READ_ONLY,
                fmt, desc, null, null);

        Chunky.getCommonThreads().submit(() -> IntStream.range(0, textureResolution + 1).parallel().forEach(i -> {
            byte[] row = new byte[(textureResolution + 1) * 4];
            Ray ray = new Ray();
            for (int j = 0; j < textureResolution + 1; j++) {
                double theta = ((double) i / textureResolution) * 2 * FastMath.PI;
                double phi = ((double) j / textureResolution) * FastMath.PI - FastMath.PI / 2;
                double r = FastMath.cos(phi);
                ray.d.set(FastMath.cos(theta) * r, FastMath.sin(phi), FastMath.sin(theta) * r);

                scene.sky().getSkyColor(ray);
                row[j*4 + 0] = (byte) (ray.color.x * 255);
                row[j*4 + 1] = (byte) (ray.color.y * 255);
                row[j*4 + 2] = (byte) (ray.color.z * 255);
                row[j*4 + 3] = (byte) 255;
            }

            clEnqueueWriteImage(instance.commandQueue, skyTexture, CL_TRUE,
                    new long[] {i, 0, 0}, new long[] {1, textureResolution+1, 1},
                    0, 0, Pointer.to(row),
                    0, null, null);
        })).join();
    }

    private static int getTextureResolution(Scene scene) {
        try {
            Sky sky = scene.sky();
            Field skyCacheField = sky.getClass().getDeclaredField("skyCache");
            skyCacheField.setAccessible(true);
            SkyCache skyCache = (SkyCache) skyCacheField.get(sky);

            return skyCache.getSkyResolution();
        } catch (NoSuchFieldException | IllegalAccessException e) {
            Log.error(e);
            throw new RuntimeException();
        }
    }
}
