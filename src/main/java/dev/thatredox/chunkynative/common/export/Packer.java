package dev.thatredox.chunkynative.common.export;

import it.unimi.dsi.fastutil.ints.IntArrayList;

/**
 * A class that packs scene data into an IntArray(List).
 */
public interface Packer {
    /**
     * Pack the data. Note: this returns an IntArrayList so the elements can be directly accessed by
     * calling {@code IntArrayList.elements()} and thus saving a lot of memory in a temporary array.
     */
    IntArrayList pack();
}
