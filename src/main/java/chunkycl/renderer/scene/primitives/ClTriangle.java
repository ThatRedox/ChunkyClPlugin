package chunkycl.renderer.scene.primitives;

import chunkycl.renderer.scene.ClMaterial;
import chunkycl.renderer.scene.ClMaterialPalette;
import chunkycl.renderer.scene.ClTextureAtlas;
import se.llbit.math.primitive.TexturedTriangle;


public class ClTriangle {
    public static void preload(TexturedTriangle triangle, ClTextureAtlas.AtlasBuilder builder) {
        builder.addTexture(triangle.material.texture);
    }

    public float e1x, e1y, e1z;
    public float e2x, e2y, e2z;
    public float ox, oy, oz;
    public float nx, ny, nz;
    public float t1u, t1v;
    public float t2u, t2v;
    public float t3u, t3v;
    public int material;
    public int flags = 0;

    public ClTriangle(TexturedTriangle triangle, ClTextureAtlas texMap, ClMaterialPalette.Builder materialBuilder) {
        this.e1x = (float) triangle.e1.x;
        this.e1y = (float) triangle.e1.y;
        this.e1z = (float) triangle.e1.z;
        this.e2x = (float) triangle.e2.x;
        this.e2y = (float) triangle.e2.y;
        this.e2z = (float) triangle.e2.z;
        this.ox = (float) triangle.o.x;
        this.oy = (float) triangle.o.y;
        this.oz = (float) triangle.o.z;
        this.nx = (float) triangle.n.x;
        this.ny = (float) triangle.n.y;
        this.nz = (float) triangle.n.z;
        this.t1u = (float) triangle.t1u;
        this.t1v = (float) triangle.t1v;
        this.t2u = (float) triangle.t2u;
        this.t2v = (float) triangle.t2v;
        this.t3u = (float) triangle.t3u;
        this.t3v = (float) triangle.t3v;
        if (triangle.doubleSided) {
            flags |= 1;
        }
        this.material = materialBuilder.addMaterial(
                new ClMaterial(triangle.material, null, texMap));
    }

    public int[] pack() {
        int[] out = new int[20];
        out[0] = Float.floatToIntBits(this.e1x);
        out[1] = Float.floatToIntBits(this.e1y);
        out[2] = Float.floatToIntBits(this.e1z);
        out[3] = Float.floatToIntBits(this.e2x);
        out[4] = Float.floatToIntBits(this.e2y);
        out[5] = Float.floatToIntBits(this.e2z);
        out[6] = Float.floatToIntBits(this.ox);
        out[7] = Float.floatToIntBits(this.oy);
        out[8] = Float.floatToIntBits(this.oz);
        out[9] = Float.floatToIntBits(this.nx);
        out[10] = Float.floatToIntBits(this.ny);
        out[11] = Float.floatToIntBits(this.nz);
        out[12] = Float.floatToIntBits(this.t1u);
        out[13] = Float.floatToIntBits(this.t1v);
        out[14] = Float.floatToIntBits(this.t2u);
        out[15] = Float.floatToIntBits(this.t2v);
        out[16] = Float.floatToIntBits(this.t3u);
        out[17] = Float.floatToIntBits(this.t3v);
        out[18] = this.material;
        out[19] = this.flags;
        return out;
    }
}
