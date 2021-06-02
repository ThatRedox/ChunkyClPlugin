package chunkycl;

import se.llbit.chunky.main.Chunky;
import se.llbit.chunky.renderer.DefaultRenderManager;
import se.llbit.chunky.renderer.Renderer;
import se.llbit.chunky.renderer.ResetReason;
import se.llbit.chunky.renderer.scene.Camera;
import se.llbit.chunky.renderer.scene.Scene;
import se.llbit.math.Ray;

import java.util.function.BooleanSupplier;
import java.util.stream.IntStream;

public abstract class AbstractOpenClRenderer implements Renderer {
    public int drawDepth = 256;
    public int lastReset = 0;
    public boolean drawEntities = true;
    public boolean sunSampling = true;

    protected BooleanSupplier callback = () -> true;
    protected GpuRayTracer rayTracer = GpuRayTracer.getTracer();

    @Override
    public abstract String getId();

    @Override
    public abstract String getName();

    @Override
    public abstract String getDescription();

    @Override
    public abstract void render(DefaultRenderManager manager) throws InterruptedException;

    @Override
    public void sceneReset(DefaultRenderManager manager, ResetReason reason, int resetCount) {
        if (resetCount != lastReset) {
            if (reason.overwriteState())
                rayTracer.generateSky(manager.bufferedScene);

            if (reason == ResetReason.MATERIALS_CHANGED || reason == ResetReason.SCENE_LOADED)
                rayTracer.load(manager.bufferedScene, manager.getRenderTask());

            lastReset = resetCount;
        }
    }

    @Override
    public void setPostRender(BooleanSupplier callback) {
        this.callback = callback;
    }

    protected float[] generateCameraRays(Scene bufferedScene) {
        // Generate camera starting rays
        int width = bufferedScene.canvasWidth();
        int height = bufferedScene.canvasHeight();

        double halfWidth = width / (2.0 * height);
        double invHeight = 1.0 / height;

        float[] rayDirs = new float[width * height * 3];

        Camera cam = bufferedScene.camera();

        Chunky.getCommonThreads().submit(() -> IntStream.range(0, width).parallel().forEach(i -> {
            Ray ray = new Ray();
            for (int j = 0; j < height; j++) {
                int offset = (j * width + i) * 3;
                cam.calcViewRay(ray, -halfWidth + i * invHeight, -0.5 + j*invHeight);

                rayDirs[offset + 0] = (float) ray.d.x;
                rayDirs[offset + 1] = (float) ray.d.y;
                rayDirs[offset + 2] = (float) ray.d.z;
            }
        })).join();

        return rayDirs;
    }

    protected float[] generateJitterLengths(float[] rayDirs, Scene bufferedScene) {
        int width = bufferedScene.canvasWidth();
        int height = bufferedScene.canvasHeight();

        double halfWidth = width / (2.0 * height);
        double invHeight = 1.0 / height;

        float[] jitterDirs = new float[width * height * 3];

        Camera cam = bufferedScene.camera();

        Chunky.getCommonThreads().submit(() -> IntStream.range(0, width).parallel().forEach(i -> {
            Ray ray = new Ray();
            for (int j = 0; j < height; j++) {
                int offset = (j * width + i) * 3;
                cam.calcViewRay(ray, -halfWidth + (i+1)*invHeight, -0.5 + (j+1)*invHeight);
                jitterDirs[offset + 0] = (float) ray.d.x - rayDirs[offset + 0];
                jitterDirs[offset + 1] = (float) ray.d.y - rayDirs[offset + 1];
                jitterDirs[offset + 2] = (float) ray.d.z - rayDirs[offset + 2];
            }
        })).join();

        return jitterDirs;
    }
}
