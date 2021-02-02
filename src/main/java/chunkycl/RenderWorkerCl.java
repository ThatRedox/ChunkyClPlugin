package chunkycl;

import se.llbit.chunky.renderer.scene.Scene;
import se.llbit.log.Log;

public class RenderWorkerCl extends Thread {
    private static final int SLEEP_INTERVAL = 75000000;

    private final int id;
    private final RenderManagerCl manager;
    private RenderManagerCl.JobManager jobManager;

    public RenderWorkerCl(RenderManagerCl manager, int id) {
        super("3D Render Worker " + id);

        this.manager = manager;
        this.id = id;

        jobManager = manager.getJobManager();
    }

    @Override public void run () {
        try {
            while (!isInterrupted()) {
                // Wait for notification to start finalizing pixels
                synchronized (jobManager) {
                    while (!jobManager.finalize && !jobManager.preview) {
                        jobManager.wait();
                    }
                }

                long jobStart = System.nanoTime();

                Scene bufferedScene = manager.getBufferedScene();
                int threads = manager.getNumThreads();
                int width = bufferedScene.canvasWidth();
                int height = bufferedScene.canvasHeight();

                // Do this once if preview, do it continuously if rendering
                do {
                    try {
                        // Finalize pixel if (x+y) % threads == id

                        for (int i = 0; i < width; i++) {
                            for (int j = 0; j < height; j++) {
                                if ((i + j) % threads == id) {
                                    bufferedScene.finalizePixel(i, j);
                                }
                            }

                            // Sleep to manage cpu usage
                            if (System.nanoTime() - jobStart > SLEEP_INTERVAL) {
                                if (manager.getCPULoad() < 100) {
                                    double load = (100.0 - manager.getCPULoad()) / manager.getCPULoad();
                                    sleep((long) ((System.nanoTime() - jobStart) / 1000000.0 * load));
                                }

                                jobStart = System.nanoTime();
                            }
                        }
                    } catch (ArrayIndexOutOfBoundsException e) {
                        // Resize?
                    }
                } while (jobManager.finalize);

                // Notify done finalizing
                // TODO: Maybe remove?
                synchronized (jobManager) {
                    jobManager.count += 1;
                    jobManager.notifyAll();

                    while (jobManager.preview) {
                        jobManager.wait();
                    }
                }
            }
        } catch (InterruptedException e) {
            // Interrupted.
        } catch (Throwable e) {
            Log.error("Error worker " + id + " crashed with an uncaught exception.", e);
        }
    }
}
