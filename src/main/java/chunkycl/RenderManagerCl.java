package chunkycl;

import se.llbit.chunky.PersistentSettings;
import se.llbit.chunky.renderer.*;
import se.llbit.chunky.renderer.scene.Scene;
import se.llbit.chunky.resources.BitmapImage;
import se.llbit.log.Log;
import se.llbit.util.TaskTracker;

import java.util.*;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

public class RenderManagerCl extends Thread implements Renderer {
    private static final Repaintable EMPTY_CANVAS = () -> {};
    private Repaintable canvas = EMPTY_CANVAS;

    private Thread[] workers = {};
    private final Scene bufferedScene;
    private final boolean headless;
    private int numThreads;

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


    private PriorityBlockingQueue<RayCl> rtQueue;
    private PriorityBlockingQueue<RayCl> rtCheckQueue;
    private PriorityBlockingQueue<RayCl> rtCompleteQueue;

    private List<RayCl> rootRays = null;

    private final JobMonitor jobMonitor = new JobMonitor();

    public static final OctreeIntersectCl intersectCl = new OctreeIntersectCl();

    public RenderManagerCl(RenderContext context, boolean headless) {
        super("Render Manager");

        numThreads = context.numRenderThreads();
        cpuLoad = PersistentSettings.getCPULoad();

        this.context = context;

        this.headless = headless;
        bufferedScene = context.getChunky().getSceneFactory().newScene();

        rtQueue = new PriorityBlockingQueue<RayCl>(11, Comparator.comparingInt(o -> o.getRay().depth));
        rtCheckQueue = new PriorityBlockingQueue<RayCl>(11, Comparator.comparingInt(o -> o.getRay().depth));
        rtCompleteQueue = new PriorityBlockingQueue<RayCl>(11, Comparator.comparingInt(o -> o.getRay().depth));
    }

    public PriorityBlockingQueue<RayCl> getRtQueue() {
        return rtQueue;
    }

    public PriorityBlockingQueue<RayCl> getRtCheckQueue() {
        return  rtCheckQueue;
    }

    public PriorityBlockingQueue<RayCl> getRtCompleteQueue() {
        return rtCompleteQueue;
    }

    public JobMonitor getJobMonitor() {
        return jobMonitor;
    }

    public List<RayCl> getRootRays() {
        return rootRays;
    }

    public int getNumThreads() {
        return numThreads;
    }

    public synchronized void setRootRays(List<RayCl> rootRays) {
        this.rootRays = rootRays;
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
            long seed = System.currentTimeMillis();
            workers = new Thread[numThreads];
            for (int i = 0; i < numThreads; i++) {
                workers[i] = new RenderWorkerCl(this, i, seed+i);
                workers[i].start();
            }

            while (!isInterrupted()) {
                ResetReason reason = sceneProvider.awaitSceneStateChange();

                synchronized (bufferedScene) {
                    sceneProvider.withSceneProtected(scene -> {
                        if (reason.overwriteState()) {
                            bufferedScene.copyState(scene);
                            intersectCl.load(bufferedScene);
                        }
                        if (reason == ResetReason.MATERIALS_CHANGED || reason == ResetReason.SCENE_LOADED) {
                            scene.importMaterials();
                        }

                        bufferedScene.copyTransients(scene);
                        finalizeAllFrames = scene.shouldFinalizeBuffer();

                        if (reason == ResetReason.SCENE_LOADED) {
                            bufferedScene.swapBuffers();
                        }
                    });
                }

                this.setRootRays(new LinkedList<>());

                synchronized (jobMonitor) {
                    jobMonitor.setRenderingState(0);
                    jobMonitor.setRendering(true);
                    jobMonitor.notifyAll();
                }

                System.out.println("Previewing");

                while (jobMonitor.getRendering()) {
                    if (rtQueue.peek() != null) {
                        ArrayList<RayCl> renderingList = new ArrayList<>((int) intersectCl.workgroupSize);

                        while (renderingList.size() < intersectCl.workgroupSize*256 && rtQueue.peek() != null)
                            renderingList.add(rtQueue.poll());

                        intersectCl.intersect(renderingList);

                        rtCheckQueue.addAll(renderingList);
                    }
                }

                renderTask.update("Preview", 1, 1, "");

                synchronized (bufferedScene) {
                    bufferedScene.swapBuffers();
                }

                canvas.repaint();

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

    public class JobMonitor extends Object {
        private boolean rendering = false;
        private int renderingState = 0;

        public synchronized void setRendering(boolean status) {
            rendering = status;
        }

        public synchronized boolean getRendering() {
            return rendering;
        }

        public synchronized int getRenderingState() {
            return renderingState;
        }

        public synchronized void setRenderingState(int newState) {
            this.renderingState = newState;
        }
    }
}
