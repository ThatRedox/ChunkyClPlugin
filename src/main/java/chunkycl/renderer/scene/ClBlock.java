package chunkycl.renderer.scene;

import chunkycl.renderer.scene.blockmodels.ClQuad;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import se.llbit.chunky.PersistentSettings;
import se.llbit.chunky.block.AbstractModelBlock;
import se.llbit.chunky.block.Block;
import se.llbit.chunky.model.AABBModel;
import se.llbit.chunky.model.QuadModel;
import se.llbit.chunky.model.Tint;
import se.llbit.chunky.resources.Texture;
import se.llbit.math.Quad;

import java.util.Arrays;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;

public class ClBlock {
    public int modelType;
    public int modelPointer;

    public ClBlock(Block block, ClTextureAtlas texMap, Object2IntMap<ClMaterial> materials, AtomicInteger materialCounter, IntArrayList quadModels) {
        if (block instanceof AbstractModelBlock) {
            AbstractModelBlock b = (AbstractModelBlock) block;
            // TODO: Proper models
            if (b.getModel() instanceof AABBModel) {
                AABBModel model = (AABBModel) b.getModel();
                modelType = 1;

                modelPointer = ClMaterial.getMaterialPointer(
                        new ClMaterial(block.texture, Tint.NONE, block.emittance, block.specular, block.metalness, block.roughness, texMap),
                        materials, materialCounter);
            } else if (b.getModel() instanceof QuadModel) {
                QuadModel model = (QuadModel) b.getModel();
                modelType = 2;

                modelPointer = quadModels.size();
                quadModels.add(model.getQuads().length);
                for (int i = 0; i < model.getQuads().length; i++) {
                    Quad quad = model.getQuads()[i];
                    Texture tex = model.getTextures()[i];
                    Tint tint = null;
                    if (model.getTints() != null) {
                        tint = model.getTints()[i];
                    }

                    ClQuad q = new ClQuad(quad, tex, tint, block.emittance, block.specular, block.metalness, block.roughness,
                            texMap, materials, materialCounter);
                    quadModels.addAll(IntList.of(q.pack()));
                }
            }
        } else {
            modelType = 0;
            modelPointer = materials.size();

            modelPointer = ClMaterial.getMaterialPointer(
                    new ClMaterial(block.texture, null, block.emittance, block.specular, block.metalness, block.roughness, texMap),
                    materials, materialCounter);
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
