package dev.thatredox.chunkynative.rust.export;

import dev.thatredox.chunkynative.common.export.texture.AbstractTextureLoader;
import dev.thatredox.chunkynative.common.export.texture.TextureRecord;
import dev.thatredox.chunkynative.rust.ffi.AbstractSynchronizedRustResource;
import dev.thatredox.chunkynative.rust.ffi.SynchronizedRustResourcePalette;
import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import se.llbit.chunky.resources.Texture;

import java.util.Map;

public class RustTextureLoader extends AbstractTextureLoader implements AutoCloseable {
    private final SynchronizedRustResourcePalette mem;

    public RustTextureLoader() {
        mem = new SynchronizedRustResourcePalette();
    }

    public AbstractSynchronizedRustResource.AddressGuard getAddress() {
        return mem.getAddress();
    }

    @Override
    protected void buildTextures(Object2ObjectMap<Texture, TextureRecord> textures) {
        for (Map.Entry<Texture, TextureRecord> record : textures.entrySet()) {
            int[] data = record.getKey().getData();
            long ref = mem.put(data);
            record.getValue().set(ref);
        }
    }

    @Override
    public void close() {
        mem.close();
    }
}
