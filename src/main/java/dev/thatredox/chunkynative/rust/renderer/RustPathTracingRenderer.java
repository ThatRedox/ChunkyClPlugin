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
                    double[] srgb = new double[3];
                    int x = pixel.firstInt();
                    int y = pixel.secondInt();

                    for (int k = 0; k < sppPerPass; k++) {
                        double ox = state.random.nextDouble();
                        double oy = state.random.nextDouble();

                        cam.calcViewRay(state.ray, state.random,
                                -halfWidth + (x + ox) * invHeight,
                                -0.5 + (y + oy) * invHeight);
                        state.ray.o.sub(scene.getOrigin());
                        pt.trace(state.ray, state.random.nextLong(), rgb);

                        for (int i = 0; i < 3; i++) {
                            srgb[i] += rgb[i];
                        }
                    }

                    int offset = 3 * (y * width + x);
                    for (int i = 0; i < 3; i++) {
                        sampleBuffer[offset + i] = (sampleBuffer[offset + i] * spp + srgb[i]) * sinv;
                    }
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
