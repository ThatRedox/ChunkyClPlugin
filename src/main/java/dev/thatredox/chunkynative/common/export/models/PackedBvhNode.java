package dev.thatredox.chunkynative.common.export.models;

import dev.thatredox.chunkynative.common.export.Packer;
import dev.thatredox.chunkynative.common.export.ResourcePalette;
import dev.thatredox.chunkynative.common.export.primitives.PackedMaterial;
import dev.thatredox.chunkynative.common.export.texture.AbstractTextureLoader;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import se.llbit.math.primitive.Primitive;

import java.util.Arrays;

public class PackedBvhNode implements Packer {
    public final int[] node;

    public PackedBvhNode(int[] packed, int offset, Primitive[][] primitives,
                         AbstractTextureLoader texturePalette,
                         ResourcePalette<PackedMaterial> materialPalette,
                         ResourcePalette<PackedTriangleModel> modelPalette) {
        this.node = Arrays.copyOfRange(packed, offset, offset+7);
        if (this.node[0] <= 0) {
            node[0] = modelPalette.put(new PackedTriangleModel(
                    primitives[-node[0]], texturePalette, materialPalette));
        }
    }

    @Override
    public IntArrayList pack() {
        return new IntArrayList(node);
    }
}
