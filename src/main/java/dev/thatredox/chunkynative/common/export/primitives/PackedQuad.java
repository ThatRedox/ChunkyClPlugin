package dev.thatredox.chunkynative.common.export.primitives;

import dev.thatredox.chunkynative.common.export.texture.AbstractTextureLoader;
import dev.thatredox.chunkynative.common.export.Packer;
import dev.thatredox.chunkynative.common.export.ResourcePalette;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import se.llbit.chunky.model.Tint;
import se.llbit.chunky.resources.Texture;
import se.llbit.chunky.world.Material;
import se.llbit.math.Quad;

public class PackedQuad implements Packer {
    public final float[] vectors = new float[13];
    public final int material;
    public final int flags;

    public PackedQuad(Quad quad, Texture texture, Tint tint, Material material,
                      AbstractTextureLoader texturePalette,
                      ResourcePalette<PackedMaterial> materialPalette) {
        this.vectors[0] = (float) quad.o.x;
        this.vectors[1] = (float) quad.o.y;
        this.vectors[2] = (float) quad.o.z;
        this.vectors[3] = (float) quad.xv.x;
        this.vectors[4] = (float) quad.xv.y;
        this.vectors[5] = (float) quad.xv.z;
        this.vectors[6] = (float) quad.yv.x;
        this.vectors[7] = (float) quad.yv.y;
        this.vectors[8] = (float) quad.yv.z;
        this.vectors[9] = (float) quad.uv.x;
        this.vectors[10] = (float) quad.uv.y;
        this.vectors[11] = (float) quad.uv.z;
        this.vectors[12] = (float) quad.uv.w;
        this.material = materialPalette.put(new PackedMaterial(texture, tint, material, texturePalette));
        int flags = 0;
        if (quad.doubleSided) {
            flags |= 1;
        }
        this.flags = flags;
    }

    /**
     * Pack this Quad. This will be compressed into 15 ints:
     * 0: Origin x
     * 1: Origin y
     * 2: Origin z
     * 3: XV x
     * 4: XV y
     * 5: XV z
     * 6: YV x
     * 7: YV y
     * 8: YV z
     * 9: UV X
     * 10: UV Y
     * 11: UV Z
     * 12: UV W
     * 13: Material reference
     * 14: Flags bitfield. 1 if doublesided.
     */
    @Override
    public IntArrayList pack() {
        IntArrayList out = new IntArrayList(15);
        for (float i : vectors) out.add(Float.floatToIntBits(i));
        out.add(material);
        out.add(flags);
        return out;
    }
}
