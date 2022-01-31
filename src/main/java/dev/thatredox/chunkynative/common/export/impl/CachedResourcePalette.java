package dev.thatredox.chunkynative.common.export.impl;

import dev.thatredox.chunkynative.common.export.ResourcePalette;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;

import java.util.OptionalInt;

public abstract class CachedResourcePalette<T> implements ResourcePalette<T> {
    protected final Object2IntOpenHashMap<T> resourceMap = new Object2IntOpenHashMap<>();
    protected boolean locked = false;

    /**
     * Add a resource to the palette and get the reference. If this resource has already been put into the palette,
     * it will return the reference to the original resource.
     *
     * @throws IllegalStateException if the palette is locked. If looking for an existing resource in the palette,
     *                               use {@code get()}.
     */
    public int put(T resource) {
        if (locked) throw new IllegalStateException("Attempted to add resources to a locked palette.");
        return resourceMap.computeIntIfAbsent(resource, this::putImpl);
    }

    /**
     * Get the reference to a resource in the palette.
     */
    public OptionalInt get(T resource) {
        int value = resourceMap.getOrDefault(resource, -1);
        if (value != -1 || resourceMap.containsKey(resource))
            return OptionalInt.of(value);
        return OptionalInt.empty();
    }

    /**
     * Implementation for the palette. This will only be called once per resource. This should put the resource
     * into the palette and return a reference to the resource.
     */
    protected abstract int putImpl(T resource);

    /**
     * Lock this palette and prevent any modifications.
     */
    protected void lock() {
        locked = true;
    }
}
