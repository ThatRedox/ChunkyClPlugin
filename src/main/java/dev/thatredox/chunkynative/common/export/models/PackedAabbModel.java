package dev.thatredox.chunkynative.common.export.models;

import dev.thatredox.chunkynative.common.export.texture.AbstractTextureLoader;
import dev.thatredox.chunkynative.common.export.Packer;
import dev.thatredox.chunkynative.common.export.ResourcePalette;
import dev.thatredox.chunkynative.common.export.primitives.PackedAabb;
import dev.thatredox.chunkynative.common.export.primitives.PackedMaterial;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import se.llbit.chunky.model.AABBModel;
import se.llbit.chunky.model.Tint;
import se.llbit.chunky.resources.Texture;
import se.llbit.chunky.world.Material;
import se.llbit.math.AABB;

public class PackedAabbModel implements Packer {
    public final PackedAabb[] boxes;

    public PackedAabbModel(AABBModel model, Material material,
                           AbstractTextureLoader texturePalette,
                           ResourcePalette<PackedMaterial> materialPalette) {
        boxes = new PackedAabb[model.getBoxes().length];
        for (int i = 0; i < boxes.length; i++) {
            AABB box = model.getBoxes()[i];
            Texture[] texs = model.getTextures()[i];
            Tint[] tints = null;
            if (model.getTints() != null)
                tints = model.getTints()[i];
            AABBModel.UVMapping[] maps = null;
            if (model.getUVMapping() != null)
                maps = model.getUVMapping()[i];

            boxes[i] = new PackedAabb(box, texs, tints, maps, material, texturePalette, materialPalette);
        }
    }

    /**
     * Pack this AABB model into ints. The first integer specifies the number of
     * AABB models and the remaining integers are the {@code PackedAbb}'s.
     */
    @Override
    public IntArrayList pack() {
        IntArrayList out = new IntArrayList();
        out.add(boxes.length);
        for (PackedAabb box : boxes)
            out.addAll(box.pack());
        return out;
    }
}
