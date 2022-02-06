package dev.thatredox.chunkynative.opencl;

import dev.thatredox.chunkynative.opencl.renderer.ClSceneLoader;
import dev.thatredox.chunkynative.opencl.renderer.RendererInstance;
import javafx.scene.control.Button;
import javafx.scene.layout.VBox;
import se.llbit.chunky.Plugin;
import se.llbit.chunky.main.Chunky;
import se.llbit.chunky.main.ChunkyOptions;
import se.llbit.chunky.model.BlockModel;
import se.llbit.chunky.renderer.RenderController;
import se.llbit.chunky.ui.ChunkyFx;
import se.llbit.chunky.ui.render.AdvancedTab;
import se.llbit.chunky.ui.render.RenderControlsTab;
import se.llbit.chunky.ui.render.RenderControlsTabTransformer;
import se.llbit.log.Log;

import java.util.ArrayList;
import java.util.List;

/**
 * This plugin changes the Chunky path tracing renderer to a gpu based path tracer.
 */
public class ChunkyCl implements Plugin {
    @Override public void attach(Chunky chunky) {
        // Check if we have block models
        try {
            Class<?> test = BlockModel.class;
        } catch (NoClassDefFoundError e) {
            Log.error("ChunkyCL requires Chunky 2.5.0. Could not load block models.", e);
            return;
        }

        // Initialize the renderer now for easier debugging
        try {
            RendererInstance.get();
        } catch (UnsatisfiedLinkError e) {
            Log.error("Failed to load ChunkyCL. Could not load OpenCL native library.", e);
            return;
        }

        ClSceneLoader sceneLoader = new ClSceneLoader();
        Chunky.addRenderer(new OpenClPathTracingRenderer(sceneLoader));
        Chunky.addPreviewRenderer(new OpenClPreviewRenderer(sceneLoader));

        RenderControlsTabTransformer prev = chunky.getRenderControlsTabTransformer();
        chunky.setRenderControlsTabTransformer(tabs -> {
            // First, call the previous transformer (this allows other plugins to work).
            List<RenderControlsTab> transformed = new ArrayList<>(prev.apply(tabs));

            // Get the scene
            RenderController controller = chunky.getRenderController();

            for (RenderControlsTab tab: transformed) {
                if (tab instanceof AdvancedTab) {
                    Button deviceSelectorButton = new Button("Select OpenCL Device");
                    deviceSelectorButton.setOnMouseClicked(event -> {
                        GpuSelector selector = new GpuSelector();
                        selector.show();
                    });

                    // Add OpenCL device selector after CPU Utilization
                    ((VBox) ((AdvancedTab) tab).getContent()).getChildren().add(2, deviceSelectorButton);
                }
            }

            return transformed;
        });
    }

    public static void main(String[] args) throws Exception {
        // Start Chunky normally with this plugin attached.
        Chunky.loadDefaultTextures();
        Chunky chunky = new Chunky(ChunkyOptions.getDefaults());
        new ChunkyCl().attach(chunky);
        ChunkyFx.startChunkyUI(chunky);
    }
}
