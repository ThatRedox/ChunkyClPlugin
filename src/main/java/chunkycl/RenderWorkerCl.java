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
                synchronized (jobManager) {
                    while (!jobManager.finalize) {
                        jobManager.wait();
                    }
                }

                Scene bufferedScene = manager.getBufferedScene();
                int threads = manager.getNumThreads();
                int width = bufferedScene.canvasWidth();
                int height = bufferedScene.canvasHeight();

                try {
                    for (int i = 0; i < width; i++) {
                        for (int j = 0; j < height; j++) {
                            if ((i + j) % threads == id) {
                                bufferedScene.finalizePixel(i, j);
                            }
                        }
                    }
                } catch (ArrayIndexOutOfBoundsException e) {
                    // Resize?
                }

                synchronized (jobManager) {
                    jobManager.count += 1;
                    if (jobManager.finalize) {
                        jobManager.finalize = false;
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
