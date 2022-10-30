package dev.thatredox.chunkynative.rust.renderer;

import dev.thatredox.chunkynative.rust.export.RustSceneLoader;
import dev.thatredox.chunkynative.rust.ffi.RustPathTracer;
import se.llbit.chunky.renderer.DefaultRenderManager;
import se.llbit.chunky.renderer.ResetReason;
import se.llbit.chunky.renderer.TileBasedRenderer;
import se.llbit.chunky.renderer.scene.Camera;
import se.llbit.chunky.renderer.scene.Scene;

public class RustPathTracingRenderer extends TileBasedRenderer {
    private final RustSceneLoader sceneLoader;

    public RustPathTracingRenderer(RustSceneLoader sceneLoader) {
        this.sceneLoader = sceneLoader;
    }

    @Override
    public String getId() {
        return "RustPathTracingRenderer";
    }

    @Override
    public String getName() {
        return "Rust Path-Tracing Renderer";
    }

    @Override
    public String getDescription() {
        return "Rust Path-Tracing Renderer";
    }

    @Override
    public void render(DefaultRenderManager manager) throws InterruptedException {
        Scene scene = manager.bufferedScene;

        // Ensure the scene is loaded
        sceneLoader.ensureLoad(scene);

        try (RustPathTracer pt = new RustPathTracer(sceneLoader.getOctree())) {

            int width = scene.width;
            int height = scene.height;

            int sppPerPass = manager.context.sppPerPass();
            Camera cam = scene.camera();
            double halfWidth = width / (2.0 * height);
            double invHeight = 1.0 / height;

            double[] sampleBuffer = scene.getSampleBuffer();

            while (scene.spp < scene.getTargetSpp()) {
                int spp = scene.spp;
                double sinv = 1.0 / (sppPerPass + spp);

                submitTiles(manager, (state, pixel) -> {
                    double[] rgb = new double[3];
                    int x = pixel.firstInt();
                    int y = pixel.secondInt();

                    double sr = 0;
                    double sg = 0;
                    double sb = 0;

                    for (int k = 0; k < sppPerPass; k++) {
                        double ox = state.random.nextDouble();
                        double oy = state.random.nextDouble();

                        cam.calcViewRay(state.ray, state.random,
                                -halfWidth + (x + ox) * invHeight,
                                -0.5 + (y + oy) * invHeight);
                        pt.trace(state.ray, state.random.nextLong(), rgb);

                        sr += state.ray.color.x;
                        sg += state.ray.color.y;
                        sb += state.ray.color.z;
                    }

                    int offset = 3 * (y * width + x);
                    sampleBuffer[offset + 0] = (sampleBuffer[offset + 0] * spp + sr) * sinv;
                    sampleBuffer[offset + 1] = (sampleBuffer[offset + 1] * spp + sg) * sinv;
                    sampleBuffer[offset + 2] = (sampleBuffer[offset + 2] * spp + sb) * sinv;
                });

                manager.pool.awaitEmpty();
                scene.spp += sppPerPass;
                if (postRender.getAsBoolean()) break;
            }
        }
    }

    @Override
    public void sceneReset(DefaultRenderManager manager, ResetReason reason, int resetCount) {
        sceneLoader.load(resetCount, reason, manager.bufferedScene);
    }
}
