package dev.thatredox.chunkynative.common.export.models;

import dev.thatredox.chunkynative.common.export.Packer;
import dev.thatredox.chunkynative.common.export.ResourcePalette;
import dev.thatredox.chunkynative.common.export.primitives.PackedMaterial;
import dev.thatredox.chunkynative.common.export.primitives.PackedTriangle;
import dev.thatredox.chunkynative.common.export.texture.AbstractTextureLoader;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import se.llbit.math.primitive.Primitive;
import se.llbit.math.primitive.TexturedTriangle;

import java.util.Arrays;

public class PackedTriangleModel implements Packer {
    public final PackedTriangle[] triangles;

    public PackedTriangleModel(Primitive[] primitives,
                               AbstractTextureLoader texturePalette,
                               ResourcePalette<PackedMaterial> materialPalette) {
        triangles = Arrays.stream(primitives)
                .filter(p -> p instanceof TexturedTriangle)
                .map(p -> (TexturedTriangle) p)
                .map(t -> new PackedTriangle(t, texturePalette, materialPalette))
                .toArray(PackedTriangle[]::new);
    }

    @Override
    public IntArrayList pack() {
        IntArrayList out = new IntArrayList();
        out.add(triangles.length);
        for (PackedTriangle t : triangles)
            out.addAll(t.pack());
        return out;
    }
}
