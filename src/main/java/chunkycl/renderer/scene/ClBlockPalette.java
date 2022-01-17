package chunkycl.renderer.scene;

import chunkycl.renderer.RendererInstance;
import static org.jocl.CL.*;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import org.jocl.*;

import java.util.Comparator;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import se.llbit.chunky.block.Block;
import se.llbit.chunky.chunk.BlockPalette;

public class ClBlockPalette {
    public final cl_mem blocks;
    public final cl_mem materials;
    public final cl_mem quadsModels;

    public ClBlockPalette(BlockPalette palette, ClTextureAtlas texMap) {
        RendererInstance instance = RendererInstance.get();

        List<Block> blockPalette = palette.getPalette();
        IntArrayList packed = new IntArrayList(blockPalette.size());
        Object2IntOpenHashMap<ClMaterial> materials = new Object2IntOpenHashMap<>();
        AtomicInteger materialCounter = new AtomicInteger(0);
        IntArrayList quads = new IntArrayList();

        for (Block block : blockPalette) {
            ClBlock clBlock = new ClBlock(block, texMap, materials, materialCounter, quads);
            packed.addAll(IntList.of(clBlock.pack()));
        }

        this.blocks = clCreateBuffer(instance.context, CL_MEM_READ_ONLY | CL_MEM_COPY_HOST_PTR,
                (long) Sizeof.cl_uint * packed.size(), Pointer.to(packed.toIntArray()),null);

        packed.clear();
        materials.object2IntEntrySet().stream().sorted(Comparator.comparingInt(Object2IntMap.Entry::getIntValue))
                .forEachOrdered(e -> packed.addAll(IntList.of(e.getKey().pack())));
        this.materials = clCreateBuffer(instance.context, CL_MEM_READ_ONLY | CL_MEM_COPY_HOST_PTR,
                (long) Sizeof.cl_uint * packed.size(), Pointer.to(packed.toIntArray()),null);

        this.quadsModels = clCreateBuffer(instance.context, CL_MEM_READ_ONLY | CL_MEM_COPY_HOST_PTR,
                (long) Sizeof.cl_uint * quads.size(), Pointer.to(quads.toIntArray()), null);
    }

    public static void preLoad(BlockPalette palette, ClTextureAtlas.AtlasBuilder builder) {
        List<Block> blockPalette = palette.getPalette();

        for (Block block : blockPalette) {
            ClBlock.preLoad(block, builder);
        }
    }

    public void release() {
        clReleaseMemObject(blocks);
        clReleaseMemObject(materials);
        clReleaseMemObject(quadsModels);
    }
}
