package dev.thatredox.chunkynative.rust;

import se.llbit.chunky.Plugin;
import se.llbit.chunky.main.Chunky;

public class RustPlugin implements Plugin {
    @Override
    public void attach(Chunky chunky) {
        NativeLoader.load();
    }
}
