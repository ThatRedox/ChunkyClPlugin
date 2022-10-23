package dev.thatredox.chunkynative.rust;

import dev.thatredox.chunkynative.rust.export.RustResourcePalette;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import se.llbit.chunky.Plugin;
import se.llbit.chunky.main.Chunky;

public class RustPlugin implements Plugin {
    @Override
    public void attach(Chunky chunky) {
        NativeLoader.load();

        try (RustResourcePalette palette = new RustResourcePalette()) {
            palette.put(() -> new IntArrayList(new int[] {1, 2, 3, 4, 5, 6, 7, 8, 9, 10}));
            palette.test();
        }
    }
}
