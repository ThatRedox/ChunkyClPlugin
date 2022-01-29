package dev.thatredox.chunkynative.common.export.primitives;

import dev.thatredox.chunkynative.common.export.AbstractTextureLoader;
import dev.thatredox.chunkynative.common.export.MaterialPalette;
import dev.thatredox.chunkynative.common.export.Packer;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import se.llbit.chunky.model.AABBModel;
import se.llbit.chunky.model.Tint;
import se.llbit.chunky.resources.Texture;
import se.llbit.chunky.world.Material;
import se.llbit.math.AABB;

public class PackedAabb implements Packer {
    public final float[] bounds = new float[6];
    public final int[] materials = new int[6];
    public final int flags;

    public PackedAabb(AABB box, Texture[] textures, Tint[] tints, AABBModel.UVMapping[] mappings,
                      Material material, AbstractTextureLoader texturePalette, MaterialPalette materialPalette) {
        this.bounds[0] = (float) box.xmin;
        this.bounds[1] = (float) box.xmax;
        this.bounds[2] = (float) box.ymin;
        this.bounds[3] = (float) box.ymax;
        this.bounds[4] = (float) box.zmin;
        this.bounds[5] = (float) box.zmax;

        int flags = 0;
        for (int i = 0; i < 6; i++) {
            Tint tint = null;
            if (tints != null) tint = tints[i];
            Texture tex = textures[i];
            AABBModel.UVMapping map = null;
            if (mappings != null) map = mappings[i];

            if (tex != null) {
                flags |= mapping2Flags(map) << (i * 4);
                this.materials[i] = materialPalette.addMaterial(
                        new PackedMaterial(tex, tint, material, texturePalette));
            } else {
                flags |= (0b1000) << (i * 4);
            }
        }
        this.flags = flags;
    }

    private static int mapping2Flags(AABBModel.UVMapping mapping) {
        int flipU =  0b0100;
        int flipV =  0b0010;
        int swapUV = 0b0001;

        if (mapping == null) {
            return 0;
        }

        switch (mapping) {
            case ROTATE_90:
                return flipV | swapUV;
            case ROTATE_180:
                return flipU | flipV;
            case ROTATE_270:
                return flipU | swapUV;
            case FLIP_U:
                return flipU;
            case FLIP_V:
                return flipV;
            default:
            case NONE:
                return 0;
        }
    }

    /**
     * Pack this AABB. This will be compressed into 13 ints:
     * 0: float xmin
     * 1: float xmax
     * 2: float ymin
     * 3: float ymax
     * 4: float zmin
     * 5: float zmax
     * 6: flags - 6 4-bit flags for all 6 materials.
     *            The corresponding flag can be accessed with {@code (flag >>> i*4) & 0b1111} where
     *            {@code i} is the material index. Each flag is a bitfield where 0b1000 represents
     *            no material, 0b0100 represents flip U mapping, 0b0010 represents flip V mapping,
     *            0b0001 represents swap U and V mapping. These should be applied in the given order.
     * 7: North material index
     * 8: East material index
     * 9: South material index
     * 10: West material index
     * 11: Top material index
     * 12: Bottom material index
     */
    @Override
    public IntArrayList pack() {
        IntArrayList out = new IntArrayList(13);
        for (float i : bounds) out.add(Float.floatToIntBits(i));
        out.add(flags);
        out.addAll(IntList.of(materials));
        return out;
    }
}
