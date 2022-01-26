package chunkycl.renderer.scene.primitives;

import chunkycl.renderer.scene.ClTextureAtlas;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import se.llbit.chunky.renderer.scene.Sun;

public class ClSun {
    private final int flags;
    private final int textureSize;
    private final int texture;
    private final float intensity;
    private final float altitude;
    private final float azimuth;

    public ClSun(Sun sun, ClTextureAtlas texMap) {
        flags = sun.drawTexture() ? 1 : 0;
        textureSize = ClTextureAtlas.getSize(Sun.texture);
        texture = texMap.get(Sun.texture).location;
        intensity = (float) sun.getIntensity();
        altitude = (float) sun.getAltitude();
        azimuth = (float) sun.getAzimuth();
    }

    public IntArrayList pack() {
        IntArrayList out = new IntArrayList(6);
        out.add(flags);
        out.add(textureSize);
        out.add(texture);
        out.add(Float.floatToIntBits(intensity));
        out.add(Float.floatToIntBits(altitude));
        out.add(Float.floatToIntBits(azimuth));
        return out;
    }
}
