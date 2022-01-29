package dev.thatredox.chunkynative.common.export;

import it.unimi.dsi.fastutil.Hash;
import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenCustomHashMap;
import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap;
import se.llbit.chunky.resources.Texture;

import java.util.Arrays;

public abstract class AbstractTextureLoader {
    private final static int MAX_IDENTITIES_PER_RECORD = 3;

    protected final Object2ObjectOpenCustomHashMap<Texture, TextureRecord> recordMap;
    protected final Reference2ObjectOpenHashMap<Texture, TextureRecord> identityRecordMap;
    protected boolean locked = false;

    public AbstractTextureLoader() {
        recordMap = new Object2ObjectOpenCustomHashMap<>(new Hash.Strategy<Texture>() {
            @Override
            public int hashCode(Texture o) {
                return Arrays.hashCode(o.getData());
            }

            @Override
            public boolean equals(Texture a, Texture b) {
                if (a == b) return true;
                if (a == null || b == null) return false;
                return Arrays.equals(a.getData(), b.getData()) && a.getWidth() == b.getWidth() && a.getHeight() == b.getHeight();
            }
        });
        identityRecordMap = new Reference2ObjectOpenHashMap<>();
    }

    /**
     * Get the texture record for a texture. This will either return a cached record or compute one.
     */
    public TextureRecord get(Texture texture) {
        if (texture == null) {
            throw new NullPointerException("Cannot load null texture.");
        }

        TextureRecord record;
        record = this.identityRecordMap.getOrDefault(texture, null);
        if (record != null) return record;
        record = this.recordMap.getOrDefault(texture, null);
        if (record != null) {
            if (record.identities < MAX_IDENTITIES_PER_RECORD) {
                this.identityRecordMap.put(texture, record);
                record.identities++;
            }
            return record;
        }

        if (this.locked) {
            throw new IllegalArgumentException("Attempted to put texture in locked texture loader.");
        }

        record = new TextureRecord();
        this.identityRecordMap.put(texture, record);
        this.recordMap.put(texture, record);
        return record;
    }

    /**
     * Finalize this texture loader and lock it. After this is called, all texture records are valid and can be resolved.
     */
    public void build() {
        if (this.locked) {
            throw new IllegalStateException("Attempted to build already built texture loader.");
        }
        this.locked = true;
        this.buildTextures(this.recordMap);
    }

    /**
     * Build the textures of this texture loader and make all the texture records valid and resolvable.
     */
    protected abstract void buildTextures(Object2ObjectMap<Texture, TextureRecord> textures);
}
