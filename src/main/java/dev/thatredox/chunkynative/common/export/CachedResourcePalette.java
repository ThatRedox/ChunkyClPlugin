package dev.thatredox.chunkynative.common.export;

import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;

/**
 * A cache wrapper around a resource palette.
 */
public class CachedResourcePalette<T> implements ResourcePalette<T> {
    protected final Object2IntOpenHashMap<T> resourceMap = new Object2IntOpenHashMap<>();

    /**
     * The wrapped resource palette.
     */
    public final ResourcePalette<T> palette;

    public CachedResourcePalette(ResourcePalette<T> palette) {
        this.palette = palette;
    }

    /**
     * Add a resource to the palette and get the reference. If this resource has already been put into the palette,
     * it will return the reference to the original resource.
     *
     * @throws IllegalStateException if the palette is locked. If looking for an existing resource in the palette,
     *                               use {@code get()}.
     */
    @Override
    public int put(T resource) {
        return resourceMap.computeIntIfAbsent(resource, palette::put);
    }

    @Override
    public void release() {
        this.palette.release();
    }
}
