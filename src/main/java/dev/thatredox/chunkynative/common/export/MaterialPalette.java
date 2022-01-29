package dev.thatredox.chunkynative.common.export;

import dev.thatredox.chunkynative.common.export.primitives.PackedMaterial;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;

import java.util.Comparator;

public class MaterialPalette implements Packer {
    private final Object2IntOpenHashMap<PackedMaterial> materials = new Object2IntOpenHashMap<>();
    private int materialCounter = 0;
    private boolean locked = false;

    /**
     * Add a material to the palette and get the reference to the material.
     */
    public int addMaterial(PackedMaterial mat) {
        if (locked) throw new IllegalStateException("Attempted to add materials to an already packed material palette.");

        int ptr = materials.getOrDefault(mat, -1);
        if (ptr == -1) {
            ptr = materialCounter;
            materialCounter += PackedMaterial.MATERIAL_DWORD_SIZE;
            materials.put(mat, ptr);
        }
        return ptr;
    }

    /**
     * Pack this material palette. This returns an array of packed materials.
     */
    @Override
    public IntArrayList pack() {
        locked = true;
        IntArrayList packed = new IntArrayList(materials.size() * PackedMaterial.MATERIAL_DWORD_SIZE);
        materials.object2IntEntrySet().stream()
                .sorted(Comparator.comparingInt(Object2IntMap.Entry::getIntValue))  // Sort by index
                .forEachOrdered(e -> packed.addAll(e.getKey().pack()));             // Pack into output
        return packed;
    }
}
