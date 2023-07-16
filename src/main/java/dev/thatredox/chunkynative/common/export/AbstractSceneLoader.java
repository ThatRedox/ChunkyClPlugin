package dev.thatredox.chunkynative.common.export;

import dev.thatredox.chunkynative.common.export.models.PackedAabbModel;
import dev.thatredox.chunkynative.common.export.models.PackedBvhNode;
import dev.thatredox.chunkynative.common.export.models.PackedQuadModel;
import dev.thatredox.chunkynative.common.export.models.PackedTriangleModel;
import dev.thatredox.chunkynative.common.export.primitives.PackedBlock;
import dev.thatredox.chunkynative.common.export.primitives.PackedMaterial;
import dev.thatredox.chunkynative.common.export.primitives.PackedSun;
import dev.thatredox.chunkynative.common.export.texture.AbstractTextureLoader;
import dev.thatredox.chunkynative.util.Reflection;
import se.llbit.chunky.renderer.ResetReason;
import se.llbit.chunky.renderer.scene.Scene;
import se.llbit.chunky.renderer.scene.SceneEntities;
import se.llbit.chunky.renderer.scene.Sun;
import se.llbit.log.Log;
import se.llbit.math.Octree;
import se.llbit.math.PackedOctree;
import se.llbit.math.bvh.BVH;
import se.llbit.math.bvh.BinaryBVH;
import se.llbit.math.primitive.TexturedTriangle;

import java.lang.ref.WeakReference;
import java.util.Arrays;

public abstract class AbstractSceneLoader {
    protected int modCount = 0;
    protected WeakReference<BVH> prevWorldBvh = new WeakReference<>(null, null);
    protected WeakReference<BVH> prevActorBvh = new WeakReference<>(null, null);
    protected WeakReference<Octree.OctreeImplementation> prevOctree = new WeakReference<>(null, null);

    protected AbstractTextureLoader texturePalette = null;
    protected ResourcePalette<PackedBlock> blockPalette = null;
    protected CachedResourcePalette<PackedMaterial> materialPalette = null;
    protected ResourcePalette<PackedAabbModel> aabbPalette = null;
    protected ResourcePalette<PackedQuadModel> quadPalette = null;
    protected ResourcePalette<PackedTriangleModel> trigPalette = null;
    protected int[] worldBvh = null;
    protected int[] actorBvh = null;
    protected PackedSun packedSun = null;

    public boolean ensureLoad(Scene scene) {
        return this.ensureLoad(scene, false);
    }

    protected boolean ensureLoad(Scene scene, boolean force) {
        if (force ||
                this.texturePalette == null || this.blockPalette == null || this.materialPalette == null ||
                this.aabbPalette == null || this.quadPalette == null || this.trigPalette == null ||
                this.prevOctree.get() != scene.getWorldOctree().getImplementation()) {
            this.modCount = -1;
            return this.load(0, ResetReason.SCENE_LOADED, scene);
        }
        return true;
    }

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
        CachedResourcePalette<PackedMaterial> materialPalette = new CachedResourcePalette<>(this.createMaterialPalette());
        ResourcePalette<PackedAabbModel> aabbPalette = this.createAabbModelPalette();
        ResourcePalette<PackedQuadModel> quadPalette = this.createQuadModelPalette();
        ResourcePalette<PackedTriangleModel> trigPalette = this.createTriangleModelPalette();

        SceneEntities entities = Reflection.getFieldValue(scene, "entities", SceneEntities.class);
        BVH worldBvh = Reflection.getFieldValue(entities, "bvh", BVH.class);
        BVH actorBvh = Reflection.getFieldValue(entities, "actorBvh", BVH.class);

        boolean needTextureLoad = resetReason == ResetReason.SCENE_LOADED ||
                resetReason == ResetReason.MATERIALS_CHANGED ||
                prevWorldBvh.get() != worldBvh ||
                prevActorBvh.get() != actorBvh;

        if (needTextureLoad) {
            if (!(worldBvh instanceof BinaryBVH || worldBvh == BVH.EMPTY)) {
                Log.error("BVH implementation must extend BinaryBVH");
                return false;
            }
            if (!(actorBvh instanceof BinaryBVH || actorBvh == BVH.EMPTY)) {
                Log.error("BVH implementation must extend BinaryBVH");
                return false;
            }
            prevWorldBvh = new WeakReference<>(worldBvh, null);
            prevActorBvh = new WeakReference<>(actorBvh, null);
        }

        // Preload textures
        if (needTextureLoad) {
            scene.getPalette().getPalette().forEach(b -> PackedBlock.preloadTextures(b, texturePalette));
            if (worldBvh != BVH.EMPTY) preloadBvh((BinaryBVH) worldBvh, texturePalette);
            if (actorBvh != BVH.EMPTY) preloadBvh((BinaryBVH) actorBvh, texturePalette);
            texturePalette.get(Sun.texture);
            texturePalette.build();
        }

        int[] blockMapping = null;
        int[] packedWorldBvh;
        int[] packedActorBvh;

        if (needTextureLoad) {
            blockMapping = scene.getPalette().getPalette().stream()
                    .mapToInt(block ->
                            blockPalette.put(new PackedBlock(block, texturePalette, materialPalette, aabbPalette, quadPalette)))
                    .toArray();
            if (worldBvh != BVH.EMPTY) {
                packedWorldBvh = loadBvh((BinaryBVH) worldBvh, texturePalette, materialPalette, trigPalette);
            } else {
                packedWorldBvh = PackedBvhNode.EMPTY_NODE.node;
            }
            if (actorBvh != BVH.EMPTY) {
                packedActorBvh = loadBvh((BinaryBVH) actorBvh, texturePalette, materialPalette, trigPalette);
            } else {
                packedActorBvh = PackedBvhNode.EMPTY_NODE.node;
            }
            packedSun = new PackedSun(scene.sun(), texturePalette);

            if (this.texturePalette != null) this.texturePalette.release();
            if (this.blockPalette != null) this.blockPalette.release();
            if (this.materialPalette != null) this.materialPalette.release();
            if (this.aabbPalette != null) this.aabbPalette.release();
            if (this.quadPalette != null) this.quadPalette.release();
            if (this.trigPalette != null) this.trigPalette.release();

            this.texturePalette = texturePalette;
            this.blockPalette = blockPalette;
            this.materialPalette = materialPalette;
            this.aabbPalette = aabbPalette;
            this.quadPalette = quadPalette;
            this.trigPalette = trigPalette;
            this.worldBvh = packedWorldBvh;
            this.actorBvh = packedActorBvh;
        }

        // Need to reload octree
        Octree.OctreeImplementation impl = scene.getWorldOctree().getImplementation();
        if (resetReason == ResetReason.SCENE_LOADED || prevOctree.get() != impl) {
            prevOctree = new WeakReference<>(impl, null);
            if (impl instanceof PackedOctree) {
                assert blockMapping != null;
                if (!loadOctree(((PackedOctree) impl).treeData, impl.getDepth(), blockMapping, blockPalette))
                    return false;
            } else {
                Log.error("Octree implementation must be PACKED");
                return false;
            }
        }

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

    protected abstract boolean loadOctree(int[] octree, int depth, int[] blockMapping, ResourcePalette<PackedBlock> blockPalette);

    protected abstract AbstractTextureLoader createTextureLoader();
    protected abstract ResourcePalette<PackedBlock> createBlockPalette();
    protected abstract ResourcePalette<PackedMaterial> createMaterialPalette();
    protected abstract ResourcePalette<PackedAabbModel> createAabbModelPalette();
    protected abstract ResourcePalette<PackedQuadModel> createQuadModelPalette();
    protected abstract ResourcePalette<PackedTriangleModel> createTriangleModelPalette();
}
