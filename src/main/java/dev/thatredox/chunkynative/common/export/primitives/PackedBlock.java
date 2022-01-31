package dev.thatredox.chunkynative.common.export.primitives;

import dev.thatredox.chunkynative.common.export.texture.AbstractTextureLoader;
import dev.thatredox.chunkynative.common.export.Packer;
import dev.thatredox.chunkynative.common.export.ResourcePalette;
import dev.thatredox.chunkynative.common.export.models.PackedAabbModel;
import dev.thatredox.chunkynative.common.export.models.PackedQuadModel;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import se.llbit.chunky.block.AbstractModelBlock;
import se.llbit.chunky.block.Block;
import se.llbit.chunky.model.AABBModel;
import se.llbit.chunky.model.QuadModel;
import se.llbit.chunky.model.Tint;

public class PackedBlock implements Packer {
    public final int modelType;
    public final int modelPointer;

    public PackedBlock(Block block, AbstractTextureLoader textureLoader,
                       ResourcePalette<PackedMaterial> materialPalette,
                       ResourcePalette<PackedAabbModel> aabbModels,
                       ResourcePalette<PackedQuadModel> quadModels) {
        if (block instanceof AbstractModelBlock) {
            AbstractModelBlock b = (AbstractModelBlock) block;
            if (b.getModel() instanceof AABBModel) {
                modelType = 2;
                modelPointer = aabbModels.put(new PackedAabbModel(
                        (AABBModel) b.getModel(), block, textureLoader, materialPalette));
            } else if (b.getModel() instanceof QuadModel) {
                modelType = 3;
                modelPointer = quadModels.put(new PackedQuadModel(
                        (QuadModel) b.getModel(), block, textureLoader, materialPalette));
            } else {
                throw new RuntimeException(String.format(
                        "Unknown model type for block %s: %s", block.name, b.getModel()));
            }
        } else if (block.invisible) {
            modelType = 0;
            modelPointer = 0;
        } else {
            modelType = 1;
            modelPointer = materialPalette.put(new PackedMaterial(block, Tint.NONE, textureLoader));
        }
    }

    /**
     * Pack this block into 2 ints. The first integer specifies the type of the model:
     * 0 - Invisible
     * 1 - Full size block
     * 2 - AABB model
     * 3 - Quad model
     * The second integer is a pointer to the model object in its respective palette.
     */
    @Override
    public IntArrayList pack() {
        IntArrayList out = new IntArrayList(2);
        out.add(modelType);
        out.add(modelPointer);
        return out;
    }
}
