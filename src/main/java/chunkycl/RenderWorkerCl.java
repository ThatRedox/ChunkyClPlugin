package chunkycl;

import org.apache.commons.math3.exception.OutOfRangeException;
import se.llbit.chunky.block.MinecraftBlock;
import se.llbit.chunky.renderer.*;
import se.llbit.chunky.renderer.scene.Camera;
import se.llbit.chunky.renderer.scene.PreviewRayTracer;
import se.llbit.chunky.renderer.scene.RayTracer;
import se.llbit.chunky.renderer.scene.Scene;
import se.llbit.chunky.world.Material;
import se.llbit.log.Log;
import se.llbit.math.*;

import java.util.*;
import java.util.concurrent.TimeUnit;

public class RenderWorkerCl extends Thread {
    private static final int SLEEP_INTERVAL = 75000000;

    private final int id;
    private final RenderManagerCl manager;

    private final WorkerState state;
    private boolean mode;

    private RenderManagerCl.JobMonitor jobMonitor;

    private double[] samples = null;

    public RenderWorkerCl(RenderManagerCl manager, int id, long seed) {
        super("3D Render Worker " + id);

        this.manager = manager;
        this.id = id;

        state = new WorkerState();
        state.random = new Random(seed);

        jobMonitor = manager.getJobMonitor();
    }

    @Override public void run () {
        long jobTime = 0;

        try {
            while (!isInterrupted()) {
                synchronized (jobMonitor) {
                    while (!jobMonitor.getRendering()) {
                        jobMonitor.wait();
                    }
                }

                long jobStart = System.nanoTime();

                work();

                jobTime += System.nanoTime() - jobStart;

                // Sleep to manage CPU utilization.
                if (jobTime > SLEEP_INTERVAL) {
                    if (manager.getCPULoad() < 100 && manager.getBufferedScene().getMode() != RenderMode.PREVIEW) {
                        // sleep = jobTime * (1-utilization) / utilization
                        double load = (100.0 - manager.getCPULoad()) / manager.getCPULoad();
                        sleep((long) (jobTime / 1000000.0 * load));
                    }
                    jobTime = 0;
                }
            }
        } catch (InterruptedException e) {
            // Interrupted.
        } catch (Throwable e) {
            Log.error("Render worker " + id + " crashed with an uncaught exception.", e);
        }
    }

    private void work() throws InterruptedException {
        if (jobMonitor.getRenderingState() == 0) {
            Scene scene = manager.getBufferedScene();
            Random random = state.random;

            int width = scene.canvasWidth();
            int height = scene.canvasHeight();

            double halfWidth = width / (2.0 * height);
            double invHeight = 1.0 / height;

            final Camera cam = scene.camera();

            for (int i = 0; i < width; i++) {
                for (int j = 0; j < height; j++) {
                    if ((i + j) % manager.getNumThreads() == id) {
                        Ray ray = new Ray();
                        cam.calcViewRay(ray, random, (-halfWidth + (double) i * invHeight),
                                (-.5 + (double) j * invHeight));

                        RayCl wrapper = new RayCl(ray, null, RayCl.TYPE.ROOT, 1);
                        wrapper.setImageCoords(new Vector2(i, j));

                        ray.o.x -= manager.getBufferedScene().getOrigin().x;
                        ray.o.y -= manager.getBufferedScene().getOrigin().y;
                        ray.o.z -= manager.getBufferedScene().getOrigin().z;

                        synchronized (manager.getRootRays()) {
                            manager.getRootRays().add(wrapper);
                        }
                        manager.getRtQueue().add(wrapper);
                    }
                }
            }

            synchronized (jobMonitor) {
                if (jobMonitor.getRenderingState() == 0) {
                    jobMonitor.setRenderingState(1);
                }
            }
        } else if (id % 2 == 0 && manager.getRtCheckQueue().size() > 0) {
            RayCl ray = manager.getRtCheckQueue().poll(1, TimeUnit.MILLISECONDS);

            // Check if ray was successfully pulled
            if (ray == null) return;

//            ray.getRay().o.x -= manager.getBufferedScene().getOrigin().x;
//            ray.getRay().o.y -= manager.getBufferedScene().getOrigin().y;
//            ray.getRay().o.z -= manager.getBufferedScene().getOrigin().z;

            ray.setIntersect(PreviewRayTracer.nextIntersection(manager.getBufferedScene(), ray.getRay()));

            manager.getRtCompleteQueue().add(ray);
        } else if (id % 2 == 1 && manager.getRtCompleteQueue().size() > 0) {
            RayCl wrapper = manager.getRtCompleteQueue().poll(1, TimeUnit.MILLISECONDS);

            // Check if ray was successfully pulled
            if (wrapper == null) return;

            Ray ray = wrapper.getRay();
            Scene scene = manager.getBufferedScene();

            // Check if ray has exited the scene
            if (!wrapper.getIntersect()) {
                // Direct sky hit.
                if (!scene.transparentSky()) {
                    scene.sky().getSkyColorInterpolated(ray);
                    scene.addSkyFog(ray);
                }
            } else {
                mapIntersection(scene, ray);
                scene.sun().flatShading(ray);
            }

//            wrapper.getRay().color.x = wrapper.getRay().distance / 256.0;
//            wrapper.getRay().color.y = wrapper.getRay().distance / 256.0;
//            wrapper.getRay().color.z = wrapper.getRay().distance / 256.0;

            wrapper.setStatus(true);
        } else if (id == 0 || (manager.getRootRays() != null && manager.getRootRays().size() > 1024) ) {
            Scene scene = manager.getBufferedScene();

            if (samples == null) {
                samples = scene.getSampleBuffer();
            }

            RayCl wrapper;
            synchronized (manager.getRootRays()) {
                wrapper = manager.getRootRays().remove(0);
            }

            if (wrapper.getStatus()) {
                Vector2 coords = wrapper.getImageCoords();
                int width = scene.canvasWidth();

                try {
                    samples[(int) ((coords.y * width + coords.x) * 3 + 0)] = wrapper.getRay().color.x;
                    samples[(int) ((coords.y * width + coords.x) * 3 + 1)] = wrapper.getRay().color.y;
                    samples[(int) ((coords.y * width + coords.x) * 3 + 2)] = wrapper.getRay().color.z;
                } catch (ArrayIndexOutOfBoundsException e) {
                    throw e;
                }

                scene.finalizePixel((int) coords.x, (int) coords.y);
            } else {
                synchronized (manager.getRootRays()) {
                    manager.getRootRays().add(wrapper);
                }
            }

            if (id == 0 && manager.getRootRays().size() == 0) {
                jobMonitor.setRendering(false);
                manager.setRootRays(null);
                samples = null;
            }
        }
    }

    private static boolean mapIntersection(Scene scene, Ray ray) {
        if (ray.d.y < 0) {
            double t = (scene.getWaterHeight() - .125 - ray.o.y - scene.getOrigin().y) / ray.d.y;
            if (t > 0 && t < ray.t) {
                Vector3 vec = new Vector3();
                vec.scaleAdd(t + Ray.OFFSET, ray.d, ray.o);
                if (!scene.isInsideOctree(vec)) {
                    ray.t = t;
                    ray.o.set(vec);
                    double xm = (ray.o.x % 16.0 + 16.0) % 16.0;
                    double zm = (ray.o.z % 16.0 + 16.0) % 16.0;
                    if (xm > 0.6 && zm > 0.6) {
                        ray.color.set(0.8, 0.8, 0.8, 1);
                    } else {
                        ray.color.set(0.25, 0.25, 0.25, 1);
                    }
                    ray.setCurrentMaterial(MinecraftBlock.STONE);
                    ray.n.set(0, 1, 0);
                    return true;
                }
            }
        }
        return false;
    }
}
