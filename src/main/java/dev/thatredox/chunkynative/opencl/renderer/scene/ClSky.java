package dev.thatredox.chunkynative.opencl.renderer.scene;


import static org.jocl.CL.*;

import dev.thatredox.chunkynative.opencl.renderer.RendererInstance;
import dev.thatredox.chunkynative.opencl.util.ClMemory;
import org.apache.commons.math3.util.FastMath;
import org.jocl.*;

import se.llbit.chunky.renderer.scene.Scene;
import se.llbit.chunky.renderer.scene.Sky;
import se.llbit.chunky.renderer.scene.SkyCache;
import se.llbit.log.Log;
import se.llbit.math.Ray;

import java.lang.reflect.Field;

public class ClSky implements AutoCloseable {
    public final ClMemory skyTexture;
    public final ClMemory skyIntensity;

    public ClSky(Scene scene) {
        int textureResolution = getTextureResolution(scene);

        RendererInstance instance = RendererInstance.get();

        this.skyIntensity = new ClMemory(clCreateBuffer(instance.context,
                CL_MEM_READ_ONLY | CL_MEM_COPY_HOST_PTR, Sizeof.cl_float,
                Pointer.to(new float[] {(float) scene.sun().getIntensity()}), null));

        cl_image_format fmt = new cl_image_format();
        fmt.image_channel_data_type = CL_UNORM_INT8;
        fmt.image_channel_order = CL_RGBA;

        cl_image_desc desc = new cl_image_desc();
        desc.image_type = CL_MEM_OBJECT_IMAGE2D;
        desc.image_width = textureResolution;
        desc.image_height = textureResolution;

        byte[] texture = new byte[textureResolution * textureResolution * 4];
        Ray ray = new Ray();
        for (int i = 0; i < textureResolution; i++) {
            for (int j = 0; j < textureResolution; j++) {
                int offset = 4 * (j * textureResolution + i);

                double theta = ((double) i / textureResolution) * 2 * FastMath.PI;
                double phi = ((double) j / textureResolution) * FastMath.PI - FastMath.PI / 2;
                double r = FastMath.cos(phi);
                ray.d.set(FastMath.cos(theta) * r, FastMath.sin(phi), FastMath.sin(theta) * r);

                scene.sky().getSkyColor(ray, false);
                texture[offset + 0] = (byte) (ray.color.x * 255);
                texture[offset + 1] = (byte) (ray.color.y * 255);
                texture[offset + 2] = (byte) (ray.color.z * 255);
                texture[offset + 3] = (byte) 255;
            }
        }

        this.skyTexture = new ClMemory(clCreateImage(instance.context, CL_MEM_READ_ONLY | CL_MEM_COPY_HOST_PTR,
                fmt, desc, Pointer.to(texture), null));
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

    @Override
    public void close() {
        skyTexture.close();
        skyIntensity.close();
    }
}
