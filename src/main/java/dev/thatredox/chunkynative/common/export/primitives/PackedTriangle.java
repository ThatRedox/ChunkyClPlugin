package dev.thatredox.chunkynative.common.export.primitives;

import dev.thatredox.chunkynative.common.export.texture.AbstractTextureLoader;
import dev.thatredox.chunkynative.common.export.Packer;
import dev.thatredox.chunkynative.common.export.ResourcePalette;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import se.llbit.chunky.model.Tint;
import se.llbit.math.primitive.TexturedTriangle;

public class PackedTriangle implements Packer {
    public final float[] vectors = new float[18];
    public final int material;
    public final int flags;

    public PackedTriangle(TexturedTriangle triangle, AbstractTextureLoader texturePalette,
                          ResourcePalette<PackedMaterial> materialPalette) {
        this.vectors[0] = (float) triangle.e1.x;
        this.vectors[1] = (float) triangle.e1.y;
        this.vectors[2] = (float) triangle.e1.z;
        this.vectors[3] = (float) triangle.e2.x;
        this.vectors[4] = (float) triangle.e2.y;
        this.vectors[5] = (float) triangle.e2.z;
        this.vectors[6] = (float) triangle.o.x;
        this.vectors[7] = (float) triangle.o.y;
        this.vectors[8] = (float) triangle.o.z;
        this.vectors[9] = (float) triangle.n.x;
        this.vectors[10] = (float) triangle.n.y;
        this.vectors[11] = (float) triangle.n.z;
        this.vectors[12] = (float) triangle.t1u;
        this.vectors[13] = (float) triangle.t1v;
        this.vectors[14] = (float) triangle.t2u;
        this.vectors[15] = (float) triangle.t2v;
        this.vectors[16] = (float) triangle.t3u;
        this.vectors[17] = (float) triangle.t3v;

        int flags = 0;
        flags |= 0x01; // Lower 8 bits signifies it is a triangle
        if (triangle.doubleSided) {
            flags |= 1 << 8; // Next 1 bit signifies if it is double sided
        }
        this.flags = flags;

        this.material = materialPalette.put(new PackedMaterial(triangle.material, Tint.NONE, texturePalette));
    }

    /**
     * Pack this Triangle. This will be compressed into 20 ints:
     * 0: Flags bitfield:
     *      Bottom 8 bits the primitive type (1 for triangle)
     *      9th bit represents if this triangle is double sided
     * 1: e1 x
     * 2: e1 y
     * 3: e1 z
     * 4: e2 x
     * 5: e2 y
     * 6: e2 z
     * 7: origin x
     * 8: origin y
     * 9: origin z
     * 10: normal x
     * 11: normal y
     * 12: normal z
     * 13: t1 u
     * 14: t1 v
     * 15: t2 u
     * 16: t2 v
     * 17: t3 u
     * 18: t3 v
     * 19: Material reference
     */
    @Override
    public IntArrayList pack() {
        IntArrayList out = new IntArrayList(20);
        out.add(flags);
        for (float i : vectors) out.add(Float.floatToIntBits(i));
        out.add(this.material);
        return out;
    }
}
