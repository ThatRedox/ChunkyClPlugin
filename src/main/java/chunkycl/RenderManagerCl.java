package chunkycl;

import se.llbit.chunky.PersistentSettings;
import se.llbit.chunky.renderer.*;
import se.llbit.chunky.renderer.scene.Camera;
import se.llbit.chunky.renderer.scene.Scene;
import se.llbit.chunky.resources.BitmapImage;
import se.llbit.log.Log;
import se.llbit.math.Ray;
import se.llbit.math.Vector3;
import se.llbit.util.TaskTracker;

import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

public class RenderManagerCl extends Thread implements Renderer {
    private static final Repaintable EMPTY_CANVAS = () -> {};
    private Repaintable canvas = EMPTY_CANVAS;

    private Thread[] workers = {};
    private final Scene bufferedScene;
    private final boolean headless;
    private int numThreads;
    private JobManager jobManager;

    private RenderMode mode = RenderMode.PREVIEW;

    private SnapshotControl snapshotControl = SnapshotControl.DEFAULT;

    private boolean finalizeAllFrames = false;

    private RenderContext context;


    private int cpuLoad;
    private SceneProvider sceneProvider;

    private BiConsumer<Long, Integer> renderCompleteListener;
    private BiConsumer<Scene, Integer> frameCompleteListener;

    private Collection<RenderStatusListener> renderListeners = new ArrayList<>();
    private Collection<SceneStatusListener> sceneListeners = new ArrayList<>();

    private TaskTracker.Task renderTask;

    public static final OctreeIntersectCl intersectCl = new OctreeIntersectCl();

    public RenderManagerCl(RenderContext context, boolean headless) {
        super("Render Manager");

        numThreads = context.numRenderThreads();
        cpuLoad = PersistentSettings.getCPULoad();
        this.jobManager = new JobManager();

        this.context = context;

        this.headless = headless;
        bufferedScene = context.getChunky().getSceneFactory().newScene();
    }

    public int getNumThreads() {
        return numThreads;
    }

    public JobManager getJobManager() {
        return jobManager;
    }

    @Override public void setSceneProvider(SceneProvider sceneProvider) {
        this.sceneProvider = sceneProvider;
    }

    @Override public void setCanvas(Repaintable canvas) {
        this.canvas = canvas;
    }

    @Override public void setCPULoad(int loadPercent) {
        this.cpuLoad = loadPercent;
    }

    public int getCPULoad() {
        return cpuLoad;
    }

    @Override public void setOnRenderCompleted(BiConsumer<Long, Integer> listener) {
        renderCompleteListener = listener;
    }

    @Override public void setOnFrameCompleted(BiConsumer<Scene, Integer> listener) {
        frameCompleteListener = listener;
    }

    @Override public void setSnapshotControl(SnapshotControl callback) {
        snapshotControl = callback;
    }

    @Override public void setRenderTask(TaskTracker.Task task) {
        renderTask = task;
    }

    @Override public synchronized void addRenderListener(RenderStatusListener listener) {
        renderListeners.add(listener);
    }

    @Override public void removeRenderListener(RenderStatusListener listener) {
        renderListeners.remove(listener);
    }

    @Override public synchronized void addSceneStatusListener(SceneStatusListener listener) {
        sceneListeners.add(listener);
    }

    @Override public void removeSceneStatusListener(SceneStatusListener listener) {
        sceneListeners.remove(listener);
    }

    @Override public void withBufferedImage(Consumer<BitmapImage> consumer) {
        bufferedScene.withBufferedImage(consumer);
    }

    @Override public void withSampleBufferProtected(SampleBufferConsumer consumer) {
        synchronized (bufferedScene) {
            consumer.accept(bufferedScene.getSampleBuffer(), bufferedScene.width, bufferedScene.height);
        }
    }

    @Override public RenderStatus getRenderStatus() {
        RenderStatus status;
        synchronized (bufferedScene) {
            status = new RenderStatus(bufferedScene.renderTime, bufferedScene.spp);
        }
        return status;
    }

    @Override public void shutdown() {
        interrupt();
    }

    public Scene getBufferedScene() {
        return bufferedScene;
    }


    @Override public void run() {
        try {
            workers = new Thread[numThreads];
            for (int i = 0; i < numThreads; i++) {
                workers[i] = new RenderWorkerCl(this, i);
                workers[i].start();
            }

            while (!isInterrupted()) {
                ResetReason reason = sceneProvider.awaitSceneStateChange();

                synchronized (bufferedScene) {
                    sceneProvider.withSceneProtected(scene -> {
                        if (reason.overwriteState()) {
                            bufferedScene.copyState(scene);
                        }
                        if (reason == ResetReason.MATERIALS_CHANGED || reason == ResetReason.SCENE_LOADED) {
                            scene.importMaterials();
                            intersectCl.load(bufferedScene, renderTask);
                        }

                        bufferedScene.copyTransients(scene);
                        finalizeAllFrames = scene.shouldFinalizeBuffer();

                        if (reason == ResetReason.SCENE_LOADED) {
                            bufferedScene.swapBuffers();

                            String sceneStatus = bufferedScene.sceneStatus();
                            synchronized (sceneListeners) {
                                for (SceneStatusListener listener : sceneListeners) {
                                    listener.sceneStatus(sceneStatus);
                                }
                            }
                        }
                    });

                    mode = bufferedScene.getMode();
                }

                if (mode == RenderMode.PREVIEW) {

                    System.out.println("Previewing");

                    previewRender();

                    renderTask.update("Preview", 1, 1, "");

                    synchronized (bufferedScene) {
                        bufferedScene.swapBuffers();
                    }

                    canvas.repaint();
                } else {
                    System.out.println("Rendering");

                    int targetSpp;
                    targetSpp = bufferedScene.getTargetSpp();

                    finalRenderer(targetSpp, renderTask);
                }

                if (headless) {
                    break;
                }
            }
        } catch (InterruptedException e) {
            // 3D view was closed.
        } catch (Throwable e) {
            Log.error("Unchecked exception in render manager", e);
        }
    }

    private void previewRender() throws InterruptedException {
        int width = bufferedScene.canvasWidth();
        int height = bufferedScene.canvasHeight();

        double halfWidth = width / (2.0 * height);
        double invHeight = 1.0 / height;

        float[] rayDirs = new float[width * height * 3];

        Camera cam = bufferedScene.camera();
        Ray ray = new Ray();

        for (int i = 0; i < width; i++) {
            for (int j = 0; j < height; j++) {
                cam.calcViewRay(ray, -halfWidth + i*invHeight, -.5 +  j*invHeight);
                rayDirs[(j * width + i)*3 + 0] = (float) ray.d.x;
                rayDirs[(j * width + i)*3 + 1] = (float) ray.d.y;
                rayDirs[(j * width + i)*3 + 2] = (float) ray.d.z;
            }
        }

        Vector3 origin = ray.o;
        origin.x -= bufferedScene.getOrigin().x;
        origin.y -= bufferedScene.getOrigin().y;
        origin.z -= bufferedScene.getOrigin().z;


        double[] samples = bufferedScene.getSampleBuffer();
        float[] rendermap = new float[samples.length];

        int spp = 4;
        for (int i = 0; i < spp; i++) {
            float[] depthmap = intersectCl.intersect(rayDirs, origin, (int) System.currentTimeMillis(), 2);

            for (int j = 0; j < rendermap.length; j++) {
                rendermap[j] += depthmap[j] * (1.0 / spp);
            }
        }

        for (int i = 0; i < rendermap.length; i++) {
            samples[i] = rendermap[i];
        }

        synchronized (jobManager) {
            jobManager.count = 0;
            jobManager.finalize = false;
            jobManager.preview = true;
            jobManager.notifyAll();
        }

        synchronized (jobManager) {
            while (jobManager.count != numThreads) {
                jobManager.wait();
            }

            jobManager.preview = false;
            jobManager.notifyAll();
        }
    }

    private void finalRenderer(int targetSpp, TaskTracker.Task renderTask) throws InterruptedException {
        renderTask.update("Rendering", targetSpp, 0);

        int width = bufferedScene.canvasWidth();
        int height = bufferedScene.canvasHeight();

        double halfWidth = width / (2.0 * height);
        double invHeight = 1.0 / height;

        float[] rayDirs = new float[width * height * 3];

        Camera cam = bufferedScene.camera();
        Ray ray = new Ray();

        for (int i = 0; i < width; i++) {
            for (int j = 0; j < height; j++) {
                cam.calcViewRay(ray, -halfWidth + i*invHeight, -.5 +  j*invHeight);
                rayDirs[(j * width + i)*3 + 0] = (float) ray.d.x;
                rayDirs[(j * width + i)*3 + 1] = (float) ray.d.y;
                rayDirs[(j * width + i)*3 + 2] = (float) ray.d.z;
            }
        }

        Vector3 origin = ray.o;
        origin.x -= bufferedScene.getOrigin().x;
        origin.y -= bufferedScene.getOrigin().y;
        origin.z -= bufferedScene.getOrigin().z;

        double[] samples = bufferedScene.getSampleBuffer();
        float[] rendermap = new float[samples.length];

        long startTime = System.currentTimeMillis();

        synchronized (jobManager) {
            jobManager.count = 0;
            jobManager.finalize = true;
            jobManager.preview = false;
            jobManager.notifyAll();
        }

        for (int sample = 0; sample < targetSpp; sample++) {
            float[] depthmap = intersectCl.intersect(rayDirs, origin, (int) System.currentTimeMillis(), bufferedScene.getRayDepth());

            for (int j = 0; j < rendermap.length; j++) {
                rendermap[j] += depthmap[j] * (1.0 / targetSpp);
            }

            for (int i = 0; i < rendermap.length; i++) {
                samples[i] = rendermap[i] * ((float) targetSpp / (sample + 1));
            }

            bufferedScene.renderTime = System.currentTimeMillis() - startTime;
            bufferedScene.spp = sample + 1;
            updateRenderProgress();

            bufferedScene.swapBuffers();
            canvas.repaint();

            if (sample % 32 == 0) {
                frameCompleteListener.accept(bufferedScene, sample);
            }

            if (mode == RenderMode.PAUSED || sceneProvider.pollSceneStateChange()) {
                break;
            }
        }

        synchronized (jobManager) {
            jobManager.finalize = false;
        }

        bufferedScene.swapBuffers();
        canvas.repaint();

        renderCompleteListener.accept(bufferedScene.renderTime, samplesPerSecond());
    }

    private void updateRenderProgress() {
        double renderTime = bufferedScene.renderTime / 1000.0;

        // Notify progress listener.
        int target = bufferedScene.getTargetSpp();
        long etaSeconds = (long) (((target - bufferedScene.spp) * renderTime) / bufferedScene.spp);
        if (etaSeconds > 0) {
            int seconds = (int) ((etaSeconds) % 60);
            int minutes = (int) ((etaSeconds / 60) % 60);
            int hours = (int) (etaSeconds / 3600);
            String eta = String.format("%d:%02d:%02d", hours, minutes, seconds);
            renderTask.update("Rendering", target, bufferedScene.spp, eta);
        } else {
            renderTask.update("Rendering", target, bufferedScene.spp, "");
        }

        synchronized (this) {
            renderListeners.forEach(listener -> {
                listener.setRenderTime(bufferedScene.renderTime);
                listener.setSamplesPerSecond(samplesPerSecond());
                listener.setSpp(bufferedScene.spp);
            });
        }
    }

    private int samplesPerSecond() {
        int canvasWidth = bufferedScene.canvasWidth();
        int canvasHeight = bufferedScene.canvasHeight();
        long pixelsPerFrame = canvasWidth * canvasHeight;
        double renderTime = bufferedScene.renderTime / 1000.0;
        return (int) ((bufferedScene.spp * pixelsPerFrame) / renderTime);
    }

    public class JobManager {
        public boolean finalize = false;
        public boolean preview = false;
        public int count = 0;
    }
}
