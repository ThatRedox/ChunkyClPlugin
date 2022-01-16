package chunkycl.renderer.scene;

import se.llbit.chunky.PersistentSettings;
import se.llbit.chunky.block.AbstractModelBlock;
import se.llbit.chunky.block.Block;
import se.llbit.chunky.model.AABBModel;
import se.llbit.chunky.model.QuadModel;
import se.llbit.chunky.model.Tint;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Objects;

public class ClBlock {
    public int modelType;
    public int modelPointer;

    public ClBlock(Block block, ClTextureAtlas texMap, ArrayList<ClMaterial> materials) {
        if (block instanceof AbstractModelBlock) {
            AbstractModelBlock b = (AbstractModelBlock) block;
            // TODO: Proper models
            if (b.getModel() instanceof AABBModel) {
                AABBModel model = (AABBModel) b.getModel();
                modelType = 1;

                modelPointer = materials.size();
                materials.add(new ClMaterial(block.texture, Tint.NONE, block.emittance, block.specular, block.metalness, block.roughness, texMap));
            } else if (b.getModel() instanceof QuadModel) {
                QuadModel model = (QuadModel) b.getModel();

                modelType = 2;
                modelPointer = materials.size();
                materials.add(new ClMaterial(block.texture, Tint.NONE, block.emittance, block.specular, block.metalness, block.roughness, texMap));
            }
        } else {
            modelType = 0;
            modelPointer = materials.size();
            materials.add(new ClMaterial(block.texture, null, block.emittance, block.specular, block.metalness, block.roughness, texMap));
        }
    }

    public int[] pack() {
        int[] packed = new int[2];
        packed[0] = modelType;
        packed[1] = modelPointer;
        return packed;
    }

    public static void preLoad(Block block, ClTextureAtlas.AtlasBuilder builder) {
        if (!PersistentSettings.getSingleColorTextures()) {
            if (block instanceof AbstractModelBlock) {
                AbstractModelBlock b = (AbstractModelBlock) block;
                if (b.getModel() instanceof AABBModel) {
                    AABBModel model = (AABBModel) b.getModel();
                    Arrays.stream(model.getTextures()).forEach(t -> Arrays.stream(t).filter(Objects::nonNull).forEach(builder::addTexture));
                } else if (b.getModel() instanceof QuadModel) {
                    QuadModel model = (QuadModel) b.getModel();
                    Arrays.stream(model.getTextures()).forEach(builder::addTexture);
                }
                // TODO: Remove once block models are done
                builder.addTexture(block.texture);
            } else {
                builder.addTexture(block.texture);
            }
        }
    }
}
