package dev.thatredox.chunkynative.common.export.models;

import dev.thatredox.chunkynative.common.export.texture.AbstractTextureLoader;
import dev.thatredox.chunkynative.common.export.Packer;
import dev.thatredox.chunkynative.common.export.ResourcePalette;
import dev.thatredox.chunkynative.common.export.primitives.PackedMaterial;
import dev.thatredox.chunkynative.common.export.primitives.PackedQuad;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import se.llbit.chunky.model.QuadModel;
import se.llbit.chunky.model.Tint;
import se.llbit.chunky.resources.Texture;
import se.llbit.chunky.world.Material;
import se.llbit.math.Quad;

public class PackedQuadModel implements Packer {
    public final PackedQuad[] quads;

    public PackedQuadModel(QuadModel model, Material material,
                           AbstractTextureLoader texturePalette,
                           ResourcePalette<PackedMaterial> materialPalette) {
        quads = new PackedQuad[model.getQuads().length];
        for (int i = 0; i < quads.length; i++) {
            Quad quad = model.getQuads()[i];
            Texture tex = model.getTextures()[i];
            Tint tint = null;
            if (model.getTints() != null) {
                tint = model.getTints()[i];
            }

            quads[i] = new PackedQuad(quad, tex, tint, material, texturePalette, materialPalette);
        }
    }

    /**
     * Pack this Quad model into ints. The first integer specifies the number of
     * Quad models and the remaining integers are the {@code PackedQuad}'s.
     */
    @Override
    public IntArrayList pack() {
        IntArrayList out = new IntArrayList();
        out.add(quads.length);
        for (PackedQuad q: quads)
            out.addAll(q.pack());
        return out;
    }
}
