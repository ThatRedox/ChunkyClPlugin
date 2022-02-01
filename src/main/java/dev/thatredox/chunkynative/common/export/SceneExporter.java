package dev.thatredox.chunkynative.common.export;

import dev.thatredox.chunkynative.common.export.models.PackedAabbModel;
import dev.thatredox.chunkynative.common.export.models.PackedBvhNode;
import dev.thatredox.chunkynative.common.export.models.PackedQuadModel;
import dev.thatredox.chunkynative.common.export.models.PackedTriangleModel;
import dev.thatredox.chunkynative.common.export.primitives.PackedBlock;
import dev.thatredox.chunkynative.common.export.primitives.PackedMaterial;
import dev.thatredox.chunkynative.common.export.texture.AbstractTextureLoader;
import dev.thatredox.chunkynative.util.Reflection;
import se.llbit.chunky.renderer.ResetReason;
import se.llbit.chunky.renderer.scene.Scene;
import se.llbit.log.Log;
import se.llbit.math.Octree;
import se.llbit.math.PackedOctree;
import se.llbit.math.bvh.BVH;
import se.llbit.math.bvh.BinaryBVH;
import se.llbit.math.primitive.TexturedTriangle;

import java.lang.ref.PhantomReference;
import java.util.Arrays;

public abstract class SceneExporter {
    protected int modCount = 0;
    protected PhantomReference<BVH> prevWorldBvh = new PhantomReference<>(BVH.EMPTY, null);
    protected PhantomReference<BVH> prevActorBvh = new PhantomReference<>(BVH.EMPTY, null);

    protected AbstractTextureLoader texturePalette = null;
    protected ResourcePalette<PackedBlock> blockPalette = null;
    protected ResourcePalette<PackedAabbModel> aabbPalette = null;
    protected ResourcePalette<PackedQuadModel> quadPalette = null;
    protected ResourcePalette<PackedTriangleModel> trigPalette = null;
    protected int[] worldBvh = null;
    protected int[] actorBvh = null;

    /**
     * @return True if successfully loaded. False if loading failed.
     */
    public boolean load(int modCount, ResetReason resetReason, Scene scene) {
        // Already up to date
        if (this.modCount == modCount) {
            return true;
        }

        if (resetReason == ResetReason.NONE || resetReason == ResetReason.MODE_CHANGE) {
            this.modCount = modCount;
            return true;
        }

        AbstractTextureLoader texturePalette = this.createTextureLoader();
        ResourcePalette<PackedBlock> blockPalette = this.createBlockPalette();
        ResourcePalette<PackedMaterial> materialPalette = this.createMaterialPalette();
        ResourcePalette<PackedAabbModel> aabbPalette = this.createAabbModelPalette();
        ResourcePalette<PackedQuadModel> quadPalette = this.createQuadModelPalette();
        ResourcePalette<PackedTriangleModel> trigPalette = this.createTriangleModelPalette();

        BVH worldBvh = Reflection.getFieldValue(scene, "bvh", BVH.class);
        BVH actorBvh = Reflection.getFieldValue(scene, "actorBvh", BVH.class);

        // Preload textures
        switch (resetReason) {
            case SCENE_LOADED:
            case MATERIALS_CHANGED:
                scene.getPalette().getPalette().forEach(
                        b -> PackedBlock.preloadTextures(b, texturePalette));

            case SETTINGS_CHANGED:
                // Check for bvh change
                if (prevWorldBvh.get() != worldBvh) {
                    if (!(worldBvh instanceof BinaryBVH)) {
                        Log.error("BVH implementation must extend BinaryBVH");
                        return false;
                    }
                    preloadBvh((BinaryBVH) worldBvh, texturePalette);
                }

                if (prevActorBvh.get() != actorBvh) {
                    if (!(actorBvh instanceof BinaryBVH)) {
                        Log.error("BVH implementation must extend BinaryBVH");
                        return false;
                    }
                    preloadBvh((BinaryBVH) actorBvh, texturePalette);
                }
        }

        texturePalette.build();
        int[] blockMapping = null;
        int[] packedWorldBvh = null;
        int[] packedActorBvh = null;

        switch (resetReason) {
            case SCENE_LOADED:
            case MATERIALS_CHANGED:
                // Need to reload materials
                blockMapping = scene.getPalette().getPalette().stream().mapToInt(block ->
                        blockPalette.put(new PackedBlock(block, texturePalette, materialPalette, aabbPalette, quadPalette)))
                        .toArray();

            case SETTINGS_CHANGED:
                // Check for bvh change
                if (prevWorldBvh.get() != worldBvh) {
                    prevWorldBvh = new PhantomReference<>(worldBvh, null);
                    assert worldBvh instanceof BinaryBVH;
                    packedWorldBvh = loadBvh((BinaryBVH) worldBvh, texturePalette, materialPalette, trigPalette);
                }

                if (prevActorBvh.get() != actorBvh) {
                    prevActorBvh = new PhantomReference<>(actorBvh, null);
                    assert actorBvh instanceof BinaryBVH;
                    packedActorBvh = loadBvh((BinaryBVH) actorBvh, texturePalette, materialPalette, trigPalette);
                }
        }

        // Need to reload octree
        if (resetReason == ResetReason.SCENE_LOADED) {
            Octree.OctreeImplementation impl = scene.getWorldOctree().getImplementation();
            if (impl instanceof PackedOctree) {
                assert blockMapping != null;
                if (!loadOctree(((PackedOctree) impl).treeData, blockMapping, blockPalette))
                    return false;
            } else {
                Log.error("Octree implementation must be PACKED");
                return false;
            }
        }

        if (this.texturePalette != null) this.texturePalette.release();
        if (this.blockPalette != null) this.blockPalette.release();
        if (this.aabbPalette != null) this.aabbPalette.release();
        if (this.quadPalette != null) this.quadPalette.release();
        if (this.trigPalette != null) this.trigPalette.release();

        this.texturePalette = texturePalette;
        this.blockPalette = blockPalette;
        this.aabbPalette = aabbPalette;
        this.quadPalette = quadPalette;
        this.trigPalette = trigPalette;
        this.worldBvh = packedWorldBvh;
        this.actorBvh = packedActorBvh;

        return true;
    }

    protected static void preloadBvh(BinaryBVH bvh, AbstractTextureLoader texturePalette) {
        Arrays.stream(bvh.packedPrimitives).flatMap(Arrays::stream).forEach(primitive -> {
            if (primitive instanceof TexturedTriangle) {
                texturePalette.get(((TexturedTriangle) primitive).material.texture);
            }
        });
    }

    protected static int[] loadBvh(BinaryBVH bvh,
                                  AbstractTextureLoader texturePalette,
                                  ResourcePalette<PackedMaterial> materialPalette,
                                  ResourcePalette<PackedTriangleModel> trigPalette) {
        int[] out = new int[bvh.packed.length];
        for (int i = 0; i < out.length; i += 7) {
            PackedBvhNode node = new PackedBvhNode(bvh.packed, i, bvh.packedPrimitives, texturePalette, materialPalette, trigPalette);
            System.arraycopy(node.pack().elements(), 0, out, i, 7);
        }
        return out;
    }

    protected abstract boolean loadOctree(int[] octree, int[] blockMapping, ResourcePalette<PackedBlock> blockPalette);

    protected abstract AbstractTextureLoader createTextureLoader();
    protected abstract ResourcePalette<PackedBlock> createBlockPalette();
    protected abstract ResourcePalette<PackedMaterial> createMaterialPalette();
    protected abstract ResourcePalette<PackedAabbModel> createAabbModelPalette();
    protected abstract ResourcePalette<PackedQuadModel> createQuadModelPalette();
    protected abstract ResourcePalette<PackedTriangleModel> createTriangleModelPalette();
}
