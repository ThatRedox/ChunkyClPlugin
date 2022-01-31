package dev.thatredox.chunkynative.common.export;

public interface ResourcePalette<T> {
    /**
     * Add a resource to the palette and get the reference.
     */
    int put(T resource);
}
