package dev.thatredox.chunkynative.common.export.primitives;

import dev.thatredox.chunkynative.common.export.texture.AbstractTextureLoader;
import dev.thatredox.chunkynative.common.export.Packer;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import se.llbit.chunky.renderer.scene.Sun;

public class PackedSun implements Packer {
    public final int flags;
    public final long texture;
    public final float intensity;
    public final float altitude;
    public final float azimuth;

    public PackedSun(Sun sun, AbstractTextureLoader texturePalette) {
        flags = sun.drawTexture() ? 1 : 0;
        texture = texturePalette.get(Sun.texture).get();
        intensity = (float) sun.getIntensity();
        altitude = (float) sun.getAltitude();
        azimuth = (float) sun.getAzimuth();
    }

    /**
     * Pack the sun into 6 ints.
     * 0: Flags. 1 if the sun should be drawn. 0 if not.
     * 1 & 2: Sun texture reference.
     * 3: float sun intensity
     * 4: float sun altitude
     * 5: float sun azimuth
     */
    @Override
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
