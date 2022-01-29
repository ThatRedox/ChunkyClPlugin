package dev.thatredox.chunkynative.opencl.renderer.scene.primitives;

import dev.thatredox.chunkynative.common.export.AbstractTextureLoader;
import dev.thatredox.chunkynative.opencl.renderer.scene.ClMaterial;
import dev.thatredox.chunkynative.opencl.renderer.scene.ClMaterialPalette;
import se.llbit.chunky.model.Tint;
import se.llbit.chunky.resources.Texture;
import se.llbit.math.Quad;

public class ClQuad {
    public float ox, oy, oz;
    public float xx, xy, xz;
    public float yx, yy, yz;
    public float uvx, uvy, uvz, uvw;
    public int material;
    public int flags;

    public ClQuad(Quad quad, Texture texture, Tint tint, float emittance, float specular, float metalness, float roughness, AbstractTextureLoader texMap, ClMaterialPalette.Builder materialBuilder) {
        this.ox = (float) quad.o.x;
        this.oy = (float) quad.o.y;
        this.oz = (float) quad.o.z;
        this.xx = (float) quad.xv.x;
        this.xy = (float) quad.xv.y;
        this.xz = (float) quad.xv.z;
        this.yx = (float) quad.yv.x;
        this.yy = (float) quad.yv.y;
        this.yz = (float) quad.yv.z;
        this.uvx = (float) quad.uv.x;
        this.uvy = (float) quad.uv.y;
        this.uvz = (float) quad.uv.z;
        this.uvw = (float) quad.uv.w;
        this.material = materialBuilder.addMaterial(
                new ClMaterial(texture, tint, emittance, specular, metalness, roughness, texMap));
        this.flags = 0;
        if (quad.doubleSided) {
            this.flags |= 1 << 31;
        }
    }

    public int[] pack() {
        int[] out = new int[15];
        out[0] = Float.floatToIntBits(ox);
        out[1] = Float.floatToIntBits(oy);
        out[2] = Float.floatToIntBits(oz);
        out[3] = Float.floatToIntBits(xx);
        out[4] = Float.floatToIntBits(xy);
        out[5] = Float.floatToIntBits(xz);
        out[6] = Float.floatToIntBits(yx);
        out[7] = Float.floatToIntBits(yy);
        out[8] = Float.floatToIntBits(yz);
        out[9] = Float.floatToIntBits(uvx);
        out[10] = Float.floatToIntBits(uvy);
        out[11] = Float.floatToIntBits(uvz);
        out[12] = Float.floatToIntBits(uvw);
        out[13] = material;
        out[14] = flags;
        return out;
    }
}
