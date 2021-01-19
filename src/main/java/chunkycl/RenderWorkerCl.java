package chunkycl;

import se.llbit.chunky.renderer.RenderContext;
import se.llbit.chunky.renderer.RenderMode;
import se.llbit.chunky.renderer.RenderStatusListener;
import se.llbit.chunky.renderer.WorkerState;
import se.llbit.chunky.renderer.scene.RayTracer;
import se.llbit.chunky.renderer.scene.Scene;
import se.llbit.log.Log;
import se.llbit.math.Ray;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Random;

public class RenderWorkerCl extends Thread {
    private static final int SLEEP_INTERVAL = 75000000;

    private final int id;
    private final RenderManagerCl manager;

    private final WorkerState state;
    private boolean mode;

    public RenderWorkerCl(RenderManagerCl manager, int id, long seed) {
        super("3D Render Worker " + id);

        this.manager = manager;
        this.id = id;

        state = new WorkerState();
        state.random = new Random(seed);
        state.ray = new Ray();

        mode = id % 2 == 0;
    }

    @Override public void run () {
        long jobTime = 0;

        try {
            while (!isInterrupted()) {
                work();
                break;
            }
        } catch (InterruptedException e) {
            // Interrupted.
        } catch (Throwable e) {
            Log.error("Render worker " + id + " crashed with an uncaught exception.", e);
        }
    }

    private void work() throws InterruptedException {

    }
}
