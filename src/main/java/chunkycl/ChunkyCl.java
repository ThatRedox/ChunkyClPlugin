package chunkycl;

import se.llbit.chunky.Plugin;
import se.llbit.chunky.main.Chunky;
import se.llbit.chunky.main.ChunkyOptions;
import se.llbit.chunky.renderer.RendererFactory;
import se.llbit.chunky.ui.ChunkyFx;

import java.lang.reflect.Field;

/**
 * This plugin changes the Chunky path tracing renderer to render ambient occlusion.
 */
public class ChunkyCl implements Plugin {
    @Override public void attach(Chunky chunky) {
        try {
            Field renderer = chunky.getClass().getDeclaredField("rendererFactory");
            renderer.setAccessible(true);
            renderer.set(chunky, (RendererFactory) RenderManagerCl::new);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            e.printStackTrace();
        }
    }

    public static void main(String[] args) throws Exception {
        // Start Chunky normally with this plugin attached.
        Chunky.loadDefaultTextures();
        Chunky chunky = new Chunky(ChunkyOptions.getDefaults());
        new ChunkyCl().attach(chunky);
        ChunkyFx.startChunkyUI(chunky);
    }
}
