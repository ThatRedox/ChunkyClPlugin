package dev.thatredox.chunkynative;

import dev.thatredox.chunkynative.opencl.ChunkyCl;
import dev.thatredox.chunkynative.rust.RustPlugin;
import se.llbit.chunky.Plugin;
import se.llbit.chunky.main.Chunky;
import se.llbit.chunky.main.ChunkyOptions;
import se.llbit.chunky.ui.ChunkyFx;

public class ChunkyNative implements Plugin {
    @Override
    public void attach(Chunky chunky) {
        new RustPlugin().attach(chunky);
        new ChunkyCl().attach(chunky);
    }

    public static void main(String[] args) throws Exception {
        // Start Chunky normally with this plugin attached.
        Chunky.loadDefaultTextures();
        Chunky chunky = new Chunky(ChunkyOptions.getDefaults());
        new ChunkyNative().attach(chunky);
        ChunkyFx.startChunkyUI(chunky);
    }
}
