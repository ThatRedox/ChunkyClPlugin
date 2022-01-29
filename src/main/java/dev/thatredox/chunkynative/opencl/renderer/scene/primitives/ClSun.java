package dev.thatredox.chunkynative.opencl.renderer.scene.primitives;

import dev.thatredox.chunkynative.opencl.export.ClTextureLoader;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import se.llbit.chunky.renderer.scene.Sun;

public class ClSun {
    private final int flags;
    private final long texture;
    private final float intensity;
    private final float altitude;
    private final float azimuth;

    public ClSun(Sun sun, ClTextureLoader texMap) {
        flags = sun.drawTexture() ? 1 : 0;
        texture = texMap.get(Sun.texture).get();
        intensity = (float) sun.getIntensity();
        altitude = (float) sun.getAltitude();
        azimuth = (float) sun.getAzimuth();
    }

    public IntArrayList pack() {
        IntArrayList out = new IntArrayList(6);
        out.add(flags);
        out.add((int) (texture >>> 32));
        out.add((int) texture);
        out.add(Float.floatToIntBits(intensity));
        out.add(Float.floatToIntBits(altitude));
        out.add(Float.floatToIntBits(azimuth));
        return out;
    }
}
