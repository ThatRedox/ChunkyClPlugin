package dev.thatredox.chunkynative.opencl.renderer.scene;

import dev.thatredox.chunkynative.opencl.renderer.RendererInstance;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import org.jocl.Pointer;
import org.jocl.Sizeof;
import org.jocl.cl_mem;

import java.util.Comparator;

import static org.jocl.CL.*;

public class ClMaterialPalette {
    public final cl_mem materials;

    public static class Builder {
        private final Object2IntOpenHashMap<ClMaterial> materials = new Object2IntOpenHashMap<>();
        private int materialCounter = 0;

        public int addMaterial(ClMaterial mat) {
            if (materials.containsKey(mat)) {
                return materials.getInt(mat);
            } else {
                int ptr = materialCounter;
                materialCounter += ClMaterial.MATERIAL_SIZE;
                materials.put(mat, ptr);
                return ptr;
            }
        }

        public ClMaterialPalette build() {
            IntArrayList packed = new IntArrayList(materials.size() * ClMaterial.MATERIAL_SIZE);
            materials.object2IntEntrySet().stream().sorted(Comparator.comparingInt(Object2IntMap.Entry::getIntValue))
                    .forEachOrdered(e -> packed.addAll(IntList.of(e.getKey().pack())));
            return new ClMaterialPalette(packed.toIntArray());
        }
    }

    private ClMaterialPalette(int[] materials) {
        RendererInstance instance = RendererInstance.get();
        this.materials = clCreateBuffer(instance.context, CL_MEM_READ_ONLY | CL_MEM_COPY_HOST_PTR,
                (long) Sizeof.cl_uint * materials.length, Pointer.to(materials), null);
    }

    public void release() {
        clReleaseMemObject(this.materials);
    }
}
