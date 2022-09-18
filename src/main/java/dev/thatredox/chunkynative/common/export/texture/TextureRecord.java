package dev.thatredox.chunkynative.common.export.texture;

public class TextureRecord {
    // Housekeeping variable used in AbstractTextureLoader
    int identities = 1;

    private long value;
    private boolean resolved = false;

    /**
     * This must only be called from the texture loader.
     * Set the value of this texture record to a 64 bit integer. This can only be called once.
     */
    public void set(long value) {
        if (resolved) {
            throw new IllegalStateException("Texture record already set. Cannot set value again.");
        }
        this.resolved = true;
        this.value = value;
    }

    /**
     * Get the value of this texture record. This must be called after the texture loader has been built.
     */
    public long get() {
        if (!resolved) {
            throw new IllegalStateException("Texture record has not been set. Cannot get the value.");
        }
        return this.value;
    }
}
