package chunkycl;

import se.llbit.chunky.renderer.DefaultRenderManager;
import se.llbit.chunky.renderer.scene.Scene;
import se.llbit.math.Vector3;
import se.llbit.util.TaskTracker;

import java.util.Arrays;
import java.util.Random;

public class OpenClPreviewRenderer extends AbstractOpenClRenderer {
    @Override
    public String getId() {
        return "ChunkyClPreviewRenderer";
    }

    @Override
    public String getName() {
        return "Chunky CL Preview Renderer";
    }

    @Override
    public String getDescription() {
        return "A work in progress OpenCL renderer.";
    }

    @Override
    public void render(DefaultRenderManager manager) throws InterruptedException {
        TaskTracker.Task renderTask = manager.getRenderTask();
        Scene bufferedScene = manager.bufferedScene;
        renderTask.update("Preview", 1, 0, "");

        // Generate randomness
        Random random = new Random(System.currentTimeMillis());

        // Generate camera rays
        float[] rayDirs = generateCameraRays(bufferedScene);

        Vector3 origin = new Vector3(bufferedScene.camera().getPosition());
        origin.sub(bufferedScene.getOrigin());

        rayTracer.previewTrace(origin, rayDirs, bufferedScene, drawDepth, drawEntities);
        manager.redrawScreen();

        callback.getAsBoolean();
        renderTask.update("Preview", 1, 1, "");
    }

    @Override
    public boolean autoPostProcess() {
        return false;
    }
}
