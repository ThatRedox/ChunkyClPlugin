package chunkycl;

import se.llbit.chunky.renderer.DefaultRenderManager;
import se.llbit.chunky.renderer.RenderWorkerPool;
import se.llbit.chunky.renderer.postprocessing.PixelPostProcessingFilter;
import se.llbit.chunky.renderer.postprocessing.PostProcessingFilter;
import se.llbit.chunky.renderer.scene.Scene;
import se.llbit.math.ColorUtil;
import se.llbit.math.QuickMath;
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
        RenderPoolMerger mergePool = new RenderPoolMerger(manager.pool, threads);
        RenderPoolFinalizer finalizePool = new RenderPoolFinalizer(manager.pool, threads);

        while (bufferedScene.spp < bufferedScene.getTargetSpp()) {
            float[] rendermap = rayTracer.rayTrace(origin, random, bufferedScene.getRayDepth(), false,
                    bufferedScene, drawDepth, drawEntities, sunSampling, cache);

            // Finalize
            if (finalizePool.isDone()) {
                manager.redrawScreen();
                finalizePool.postProcessFrame(bufferedScene);
            }

            // Wait for previous merge to finish
            mergePool.join();

            // Merge
            mergePool.merge(bufferedScene, rendermap);

            bufferedScene.spp += 1;
            if (callback.getAsBoolean()) break;
        }

        mergePool.join();
        bufferedScene.postProcessFrame(TaskTracker.NONE);
        manager.redrawScreen();
        cache.release();
    }

    @Override
    public boolean autoPostProcess() {
        return false;
    }

    private static class RenderPoolMerger {
        private RenderWorkerPool.RenderJobFuture[] jobs;
        private RenderWorkerPool pool;

        public RenderPoolMerger(RenderWorkerPool pool, int threads) {
            this.pool = pool;
            this.jobs = new RenderWorkerPool.RenderJobFuture[threads];
        }

        public void join() throws InterruptedException {
            for (RenderWorkerPool.RenderJobFuture job : jobs) {
                if (job != null) job.awaitFinish();
            }
        }

        public void merge(Scene scene, float[] renderMap) throws InterruptedException {
            this.join();

            double[] sampleBuffer = scene.getSampleBuffer();
            int sppF = scene.spp;
            double sinv = 1.0 / (sppF + 1);

            for (int i = 0; i < jobs.length; i++) {
                int finalI = i;
                int threads = jobs.length;
                jobs[i] = pool.submit(renderWorker -> {
                    for (int k = finalI; k < sampleBuffer.length; k += threads)
                        sampleBuffer[k] = (sampleBuffer[k] * sppF + renderMap[k]) * sinv;
                });
            }
        }
    }

    private static class RenderPoolFinalizer {
        private RenderWorkerPool.RenderJobFuture[] jobs;
        private RenderWorkerPool pool;

        public RenderPoolFinalizer(RenderWorkerPool pool, int threads) {
            this.pool = pool;
            this.jobs = new RenderWorkerPool.RenderJobFuture[threads];
        }

        public boolean isDone() {
            for (RenderWorkerPool.RenderJobFuture job : jobs) {
                if (job != null && !job.isDone()) return false;
            }
            return true;
        }

        public void join() throws InterruptedException {
            for (RenderWorkerPool.RenderJobFuture job : jobs) {
                if (job != null) job.awaitFinish();
            }
        }

        public void postProcessFrame(Scene scene) throws InterruptedException {
            this.join();

            PostProcessingFilter filter = scene.getPostProcessingFilter();
            if (filter instanceof PixelPostProcessingFilter) {
                PixelPostProcessingFilter pixelFilter =  (PixelPostProcessingFilter) filter;

                for (int i = 0; i < jobs.length; i++) {
                    int finalI = i;
                    double[] pixelBuffer = new double[3];
                    double[] buffer = scene.getSampleBuffer();
                    double exposure = scene.getExposure();

                    jobs[i] = pool.submit(renderWorker -> {
                        for (int x = 0; x < scene.width; x++) {
                            for (int y = 0; y < scene.height; y++) {
                                if ((x + y) % jobs.length == finalI) {
                                    pixelFilter.processPixel(scene.width, scene.height, buffer,
                                            x, y, exposure, pixelBuffer);
                                    Arrays.setAll(pixelBuffer, a -> QuickMath.clamp(pixelBuffer[a] , 0, 1));
                                    scene.getBackBuffer().setPixel(x, y, ColorUtil.getRGB(pixelBuffer));
                                }
                            }
                        }
                    });
                }
            } else {
                jobs[0] = pool.submit(renderWorker -> filter.processFrame(scene.width, scene.height,
                        scene.getSampleBuffer(), scene.getBackBuffer(), scene.getExposure(), TaskTracker.Task.NONE));
            }
        }
    }
}
