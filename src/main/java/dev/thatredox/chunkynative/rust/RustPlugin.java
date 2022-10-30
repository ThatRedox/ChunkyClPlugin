package dev.thatredox.chunkynative.rust;

import dev.thatredox.chunkynative.rust.export.RustSceneLoader;
import dev.thatredox.chunkynative.rust.renderer.RustPathTracingRenderer;
import se.llbit.chunky.Plugin;
import se.llbit.chunky.main.Chunky;

import java.lang.management.ManagementFactory;

public class RustPlugin implements Plugin {
    @Override
    public void attach(Chunky chunky) {
        System.out.println("Debugging PID: " + ManagementFactory.getRuntimeMXBean().getName());
        NativeLoader.load();

        RustSceneLoader sceneLoader = new RustSceneLoader();
        Chunky.addRenderer(new RustPathTracingRenderer(sceneLoader));
    }
}
