package dev.thatredox.chunkynative.common.export;

public interface ResourcePalette<T> {
    /**
     * Add a resource to the palette and get the reference.
     */
    int put(T resource);

    /**
     * Release this resource palette. The default implementation does nothing, but palettes with native
     * resources can use this to free those resources.
     */
    default void release() {}
}
