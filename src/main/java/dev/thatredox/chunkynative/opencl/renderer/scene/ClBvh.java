package dev.thatredox.chunkynative.opencl.renderer.scene;

import dev.thatredox.chunkynative.common.export.texture.AbstractTextureLoader;
import dev.thatredox.chunkynative.opencl.renderer.RendererInstance;
import static org.jocl.CL.*;

import dev.thatredox.chunkynative.opencl.renderer.scene.primitives.ClTriangle;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import org.jocl.Pointer;
import org.jocl.Sizeof;
import org.jocl.cl_mem;
import se.llbit.log.Log;
import se.llbit.math.bvh.BinaryBVH;
import se.llbit.math.primitive.Primitive;
import se.llbit.math.primitive.TexturedTriangle;

import java.util.Arrays;

public class ClBvh {
    public final cl_mem bvh;
    public final cl_mem trigs;

    public ClBvh(BinaryBVH bvh, AbstractTextureLoader texMap, ClMaterialPalette.Builder materialBuilder) {
        RendererInstance instance = RendererInstance.get();

        int[] packedArray = new int[bvh.packed.length];
        int[] primitivePointers = new int[bvh.packedPrimitives.length];
        IntArrayList primitives = new IntArrayList(bvh.packedPrimitives.length * BinaryBVH.SPLIT_LIMIT);

        for (int i = 0; i < bvh.packedPrimitives.length; i++) {
            Primitive[] ps = bvh.packedPrimitives[i];
            primitivePointers[i] = primitives.size();
            primitives.add((int) Arrays.stream(ps).filter(p -> p instanceof TexturedTriangle).count());
            for (Primitive p : ps) {
                if (p instanceof TexturedTriangle) {
                    ClTriangle trig = new ClTriangle((TexturedTriangle) p, texMap, materialBuilder);
                    primitives.addAll(IntList.of(trig.pack()));
                }
            }
        }

        for (int i = 0; i < bvh.packed.length; i++) {
            int value = bvh.packed[i];
            if (i % 7 == 0 && value <= 0) {
                packedArray[i] = -primitivePointers[-value];
            } else {
                packedArray[i] = bvh.packed[i];
            }
        }

        this.bvh = clCreateBuffer(instance.context, CL_MEM_READ_ONLY | CL_MEM_COPY_HOST_PTR,
                (long) Sizeof.cl_uint * packedArray.length, Pointer.to(packedArray), null);
        this.trigs = clCreateBuffer(instance.context, CL_MEM_READ_ONLY | CL_MEM_COPY_HOST_PTR,
                (long) Sizeof.cl_uint * primitives.size(), Pointer.to(primitives.toIntArray()), null);
    }

    public void release() {
        clReleaseMemObject(bvh);
        clReleaseMemObject(trigs);
    }

    public static void preload(BinaryBVH bvh, AbstractTextureLoader builder) {
        boolean warned = false;
        for (Primitive[] primitives : bvh.packedPrimitives) {
            for (Primitive primitive : primitives) {
                if (primitive instanceof TexturedTriangle) {
                    builder.get(((TexturedTriangle) primitive).material.texture);
                } else if (!warned) {
                    Log.warnf("Cannot load primitive of type: %s", primitive.getClass());
                    warned = true;
                }
            }
        }
    }
}
