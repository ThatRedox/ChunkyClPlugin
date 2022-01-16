package chunkycl.renderer.scene;

import chunkycl.renderer.RendererInstance;
import static org.jocl.CL.*;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import org.jocl.*;

import java.util.ArrayList;
import java.util.List;

import se.llbit.chunky.block.Block;
import se.llbit.chunky.chunk.BlockPalette;

public class ClBlockPalette {
    public final cl_mem blocks;
    public final cl_mem materials;

    public ClBlockPalette(BlockPalette palette, ClTextureAtlas texMap) {
        RendererInstance instance = RendererInstance.get();

        List<Block> blockPalette = palette.getPalette();
        IntArrayList packed = new IntArrayList(blockPalette.size());
        ArrayList<ClMaterial> materials = new ArrayList<>();

        for (Block block : blockPalette) {
            ClBlock clBlock = new ClBlock(block, texMap, materials);
            packed.addAll(IntList.of(clBlock.pack()));
        }

        this.blocks = clCreateBuffer(instance.context, CL_MEM_READ_ONLY | CL_MEM_COPY_HOST_PTR,
                (long) Sizeof.cl_uint * packed.size(), Pointer.to(packed.toIntArray()),null);

        packed.clear();
        materials.forEach(mat -> packed.addAll(IntList.of(mat.pack())));
        this.materials = clCreateBuffer(instance.context, CL_MEM_READ_ONLY | CL_MEM_COPY_HOST_PTR,
                (long) Sizeof.cl_uint * packed.size(), Pointer.to(packed.toIntArray()),null);
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
    }
}
