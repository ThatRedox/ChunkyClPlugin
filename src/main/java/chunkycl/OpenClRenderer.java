package chunkycl;

import se.llbit.chunky.renderer.DefaultRenderManager;
import se.llbit.chunky.renderer.postprocessing.PixelPostProcessingFilter;
import se.llbit.chunky.renderer.postprocessing.PostProcessingFilter;
import se.llbit.chunky.renderer.scene.Scene;
import se.llbit.math.ColorUtil;
import se.llbit.math.Ray;
import se.llbit.math.Vector3;
import se.llbit.util.TaskTracker;

import java.util.Arrays;
import java.util.Random;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ForkJoinTask;
import java.util.stream.IntStream;

public class OpenClRenderer extends AbstractOpenClRenderer {
    @Override
    public String getId() {
        return "ChunkyClRenderer";
    }

    @Override
    public String getName() {
        return "Chunky CL Renderer";
    }

    @Override
    public String getDescription() {
        return "A work in progress OpenCL renderer.";
    }

    @Override
    public void render(DefaultRenderManager manager) throws InterruptedException {
        // Reload scene
        rayTracer.generateSky(manager.bufferedScene);
        rayTracer.load(manager.bufferedScene, manager.getRenderTask());

        // Get information
        TaskTracker.Task renderTask = manager.getRenderTask();
        Scene bufferedScene = manager.bufferedScene;
        renderTask.update("Rendering", bufferedScene.getTargetSpp(), bufferedScene.spp);

        // Generate randomness
        Random random = new Random(System.currentTimeMillis());

        // Generate camera rays
        float[] rayDirs = generateCameraRays(bufferedScene);
        float[] jitterDirs = generateJitterLengths(rayDirs, bufferedScene);

        Ray testRay = new Ray();
        bufferedScene.camera().calcViewRay(testRay, 0, 0);


        Vector3 origin = new Vector3(bufferedScene.camera().getPosition());
        origin.sub(bufferedScene.getOrigin());

        // Sample buffer
        double[] samples = bufferedScene.getSampleBuffer();

        // Generate raytracing cache
        GpuRayTracer.RayTraceCache cache = rayTracer.createCache(rayDirs, jitterDirs);

        // Create work pools
        int threads = Math.max(manager.pool.threads/2, 1);
        ForkJoinPool mergePool = new ForkJoinPool(threads);
        ForkJoinPool finalizePool = new ForkJoinPool(threads);

        // Merge task
        ForkJoinTask mergeTask = null;
        ForkJoinTask finalizeTask = null;

        while (bufferedScene.spp < bufferedScene.getTargetSpp()) {
            float[] rendermap = rayTracer.rayTrace(origin, random, bufferedScene.getRayDepth(), false,
                    bufferedScene, drawDepth, drawEntities, sunSampling, cache);

            // Finalize
            if (finalizeTask == null || finalizeTask.isDone()) {
                if (finalizeTask != null) {
                    finalizeTask.join();
                    manager.redrawScreen();
                }

                finalizeTask = finalizePool.submit(() -> {
                    PostProcessingFilter filter = bufferedScene.getPostProcessingFilter();
                    if (filter instanceof PixelPostProcessingFilter) {
                        PixelPostProcessingFilter pixelFilter =  (PixelPostProcessingFilter) filter;

                        IntStream.range(0, bufferedScene.width).parallel().forEach(i -> {
                            double[] pixelBuffer = new double[3];
                            double[] buffer = bufferedScene.getSampleBuffer();
                            double exposure = bufferedScene.getExposure();
                            for (int j = 0; j < bufferedScene.height; j++) {
                                pixelFilter.processPixel(bufferedScene.width, bufferedScene.height, buffer,
                                        i, j, exposure, pixelBuffer);
                                bufferedScene.getBackBuffer().setPixel(i, j, ColorUtil.getRGB(pixelBuffer));
                            }
                        });
                    } else {
                        filter.processFrame(bufferedScene.width, bufferedScene.height, bufferedScene.getSampleBuffer(),
                                bufferedScene.getBackBuffer(), bufferedScene.getExposure(), TaskTracker.Task.NONE);
                    }
                });
            }

            // Wait for previous merge to finish
            if (mergeTask != null) mergeTask.join();

            // Merge
            int sppF = bufferedScene.spp;
            double sinv = 1.0 / (sppF + 1);
            mergeTask = mergePool.submit(() -> Arrays.parallelSetAll(samples, i -> (samples[i] * sppF + rendermap[i]) * sinv));

            bufferedScene.spp += 1;
            if (callback.getAsBoolean()) break;
        }

        if (mergeTask != null) mergeTask.join();
        bufferedScene.postProcessFrame(TaskTracker.NONE);
        manager.redrawScreen();
        cache.release();
    }

    @Override
    public boolean autoPostProcess() {
        return false;
    }
}
