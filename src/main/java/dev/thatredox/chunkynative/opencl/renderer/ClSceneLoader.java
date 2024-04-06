package dev.thatredox.chunkynative.opencl.renderer;

import dev.thatredox.chunkynative.common.export.AbstractSceneLoader;
import dev.thatredox.chunkynative.common.export.ResourcePalette;
import dev.thatredox.chunkynative.common.export.models.PackedAabbModel;
import dev.thatredox.chunkynative.common.export.models.PackedQuadModel;
import dev.thatredox.chunkynative.common.export.models.PackedTriangleModel;
import dev.thatredox.chunkynative.common.export.primitives.PackedBlock;
import dev.thatredox.chunkynative.common.export.primitives.PackedMaterial;
import dev.thatredox.chunkynative.common.export.primitives.PackedSun;
import dev.thatredox.chunkynative.common.export.texture.AbstractTextureLoader;
import dev.thatredox.chunkynative.common.state.SkyState;
import dev.thatredox.chunkynative.opencl.context.ClContext;
import dev.thatredox.chunkynative.opencl.context.ContextManager;
import dev.thatredox.chunkynative.opencl.renderer.export.ClPackedResourcePalette;
import dev.thatredox.chunkynative.opencl.renderer.export.ClTextureLoader;
import dev.thatredox.chunkynative.opencl.renderer.scene.ClSky;
import dev.thatredox.chunkynative.opencl.util.ClIntBuffer;
import dev.thatredox.chunkynative.util.FunctionCache;
import se.llbit.chunky.renderer.ResetReason;
import se.llbit.chunky.renderer.scene.Scene;

import java.util.Arrays;

public class ClSceneLoader extends AbstractSceneLoader {
    protected final FunctionCache<int[], ClIntBuffer> clWorldBvh;
    protected final FunctionCache<int[], ClIntBuffer> clActorBvh;
    protected final FunctionCache<PackedSun, ClIntBuffer> clPackedSun;
    protected ClSky clSky = null;
    protected SkyState skyState = null;

    protected ClIntBuffer octreeData = null;
    protected ClIntBuffer octreeDepth = null;
    private final ClContext context;

    public ClSceneLoader(ClContext context) {
        this.context = context;
        this.clWorldBvh = new FunctionCache<>(i -> new ClIntBuffer(i, context), ClIntBuffer::close, null);
        this.clActorBvh = new FunctionCache<>(i -> new ClIntBuffer(i, context), ClIntBuffer::close, null);
        this.clPackedSun = new FunctionCache<>(i -> new ClIntBuffer(i, context), ClIntBuffer::close, null);
    }

    @Override
    public boolean ensureLoad(Scene scene) {
        return this.ensureLoad(scene, clSky == null);
    }

    @Override
    public boolean load(int modCount, ResetReason resetReason, Scene scene) {
        boolean loadSuccess = super.load(modCount, resetReason, scene);
        if (this.modCount != modCount) {
            SkyState newSky = new SkyState(scene.sky(), scene.sun());
            if (!newSky.equals(skyState)) {
                if (clSky != null) clSky.close();
                clSky = new ClSky(scene, context);
                skyState = newSky;
                packedSun = new PackedSun(scene.sun(), getTexturePalette());
            }
        }
        return loadSuccess;
    }

    @Override
    protected boolean loadOctree(int[] octree, int depth, int[] blockMapping, ResourcePalette<PackedBlock> blockPalette) {
        if (octreeData != null) octreeData.close();
        if (octreeDepth != null) octreeDepth.close();

        int[] mappedOctree = Arrays.stream(octree)
                .map(i -> i > 0 || -i >= blockMapping.length ? i : -blockMapping[-i])
                .toArray();
        octreeData = new ClIntBuffer(mappedOctree, context);
        octreeDepth = new ClIntBuffer(depth, context);

        return true;
    }

    @Override
    protected AbstractTextureLoader createTextureLoader() {
        return new ClTextureLoader(context);
    }

    @Override
    protected ResourcePalette<PackedBlock> createBlockPalette() {
        return new ClPackedResourcePalette<>(context);
    }

    @Override
    protected ResourcePalette<PackedMaterial> createMaterialPalette() {
        return new ClPackedResourcePalette<>(context);
    }

    @Override
    protected ResourcePalette<PackedAabbModel> createAabbModelPalette() {
        return new ClPackedResourcePalette<>(context);
    }

    @Override
    protected ResourcePalette<PackedQuadModel> createQuadModelPalette() {
        return new ClPackedResourcePalette<>(context);
    }

    @Override
    protected ResourcePalette<PackedTriangleModel> createTriangleModelPalette() {
        return new ClPackedResourcePalette<>(context);
    }

    public ClIntBuffer getOctreeData() {
        assert octreeData != null;
        return octreeData;
    }

    public ClIntBuffer getOctreeDepth() {
        assert octreeDepth != null;
        return octreeDepth;
    }

    public ClTextureLoader getTexturePalette() {
        assert texturePalette instanceof ClTextureLoader;
        return (ClTextureLoader) texturePalette;
    }

    public ClPackedResourcePalette<PackedBlock> getBlockPalette() {
        assert blockPalette instanceof ClPackedResourcePalette;
        return (ClPackedResourcePalette<PackedBlock>) blockPalette;
    }

    public ClPackedResourcePalette<PackedMaterial> getMaterialPalette() {
        assert materialPalette.palette instanceof ClPackedResourcePalette;
        return (ClPackedResourcePalette<PackedMaterial>) materialPalette.palette;
    }

    public ClPackedResourcePalette<PackedAabbModel> getAabbPalette() {
        assert aabbPalette instanceof ClPackedResourcePalette;
        return (ClPackedResourcePalette<PackedAabbModel>) aabbPalette;
    }

    public ClPackedResourcePalette<PackedQuadModel> getQuadPalette() {
        assert quadPalette instanceof ClPackedResourcePalette;
        return (ClPackedResourcePalette<PackedQuadModel>) quadPalette;
    }

    public ClPackedResourcePalette<PackedTriangleModel> getTrigPalette() {
        assert trigPalette instanceof ClPackedResourcePalette;
        return (ClPackedResourcePalette<PackedTriangleModel>) trigPalette;
    }

    public ClIntBuffer getWorldBvh() {
        return clWorldBvh.apply(this.worldBvh);
    }

    public ClIntBuffer getActorBvh() {
        return clActorBvh.apply(this.actorBvh);
    }

    public ClSky getSky() {
        assert clSky != null;
        return clSky;
    }

    public ClIntBuffer getSun() {
        return clPackedSun.apply(packedSun);
    }
}
