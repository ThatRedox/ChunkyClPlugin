package dev.thatredox.chunkynative.rust.export;

import dev.thatredox.chunkynative.common.export.AbstractSceneLoader;
import dev.thatredox.chunkynative.common.export.ResourcePalette;
import dev.thatredox.chunkynative.common.export.models.PackedAabbModel;
import dev.thatredox.chunkynative.common.export.models.PackedQuadModel;
import dev.thatredox.chunkynative.common.export.models.PackedTriangleModel;
import dev.thatredox.chunkynative.common.export.primitives.PackedBlock;
import dev.thatredox.chunkynative.common.export.primitives.PackedMaterial;
import dev.thatredox.chunkynative.common.export.texture.AbstractTextureLoader;
import dev.thatredox.chunkynative.rust.export.ffi.AbstractSynchronizedRustResource;
import dev.thatredox.chunkynative.rust.export.ffi.SynchronizedRustBvh;
import dev.thatredox.chunkynative.rust.export.ffi.SynchronizedRustOctree;
import dev.thatredox.chunkynative.util.FunctionCache;

public class RustSceneLoader extends AbstractSceneLoader {
    protected SynchronizedRustOctree octree = null;
    protected FunctionCache<int[], SynchronizedRustBvh> rustWorldBvh = new FunctionCache<>(SynchronizedRustBvh::new, SynchronizedRustBvh::close, null);
    protected FunctionCache<int[], SynchronizedRustBvh> rustActorBvh = new FunctionCache<>(SynchronizedRustBvh::new, SynchronizedRustBvh::close, null);

    @Override
    protected boolean loadOctree(int[] octree, int depth, int[] blockMapping, ResourcePalette<PackedBlock> blockPalette) {
        if (this.octree != null) {
            this.octree.close();
        }
        this.octree = new SynchronizedRustOctree(octree, depth, blockMapping);
        return true;
    }

    @Override
    protected AbstractTextureLoader createTextureLoader() {
        return new RustTextureLoader();
    }

    @Override
    protected ResourcePalette<PackedBlock> createBlockPalette() {
        return new RustResourcePalette<>();
    }

    @Override
    protected ResourcePalette<PackedMaterial> createMaterialPalette() {
        return new RustResourcePalette<>();
    }

    @Override
    protected ResourcePalette<PackedAabbModel> createAabbModelPalette() {
        return new RustResourcePalette<>();
    }

    @Override
    protected ResourcePalette<PackedQuadModel> createQuadModelPalette() {
        return new RustResourcePalette<>();
    }

    @Override
    protected ResourcePalette<PackedTriangleModel> createTriangleModelPalette() {
        return new RustResourcePalette<>();
    }

    public AbstractSynchronizedRustResource.AddressGuard getOctree() {
        return octree.getAddress();
    }

    public AbstractSynchronizedRustResource.AddressGuard getTexturePalette() {
        assert texturePalette instanceof RustTextureLoader;
        return ((RustTextureLoader) texturePalette).getAddress();
    }

    public AbstractSynchronizedRustResource.AddressGuard getBlockPalette() {
        assert blockPalette instanceof RustResourcePalette;
        return ((RustResourcePalette<PackedBlock>) blockPalette).getAddress();
    }

    public AbstractSynchronizedRustResource.AddressGuard getMaterialPalette() {
        assert materialPalette.palette instanceof RustResourcePalette;
        return ((RustResourcePalette<PackedMaterial>) materialPalette.palette).getAddress();
    }

    public AbstractSynchronizedRustResource.AddressGuard getAabbPalette() {
        assert aabbPalette instanceof RustResourcePalette;
        return ((RustResourcePalette<PackedAabbModel>) aabbPalette).getAddress();
    }

    public AbstractSynchronizedRustResource.AddressGuard getQuadPalette() {
        assert quadPalette instanceof RustResourcePalette;
        return ((RustResourcePalette<PackedQuadModel>) quadPalette).getAddress();
    }

    public AbstractSynchronizedRustResource.AddressGuard getTrigPalette() {
        assert trigPalette instanceof RustResourcePalette;
        return ((RustResourcePalette<PackedTriangleModel>) trigPalette).getAddress();
    }

    public AbstractSynchronizedRustResource.AddressGuard getWorldBvh() {
        return rustWorldBvh.apply(this.worldBvh).getAddress();
    }

    public AbstractSynchronizedRustResource.AddressGuard getActorBvh() {
        return rustActorBvh.apply(this.actorBvh).getAddress();
    }
}
