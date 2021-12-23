package chunkycl.renderer.scene;

import chunkycl.renderer.RendererInstance;
import static org.jocl.CL.*;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import org.jocl.*;

import java.util.List;

import se.llbit.chunky.block.Block;
import se.llbit.chunky.chunk.BlockPalette;

public class ClBlockPalette {
    public final cl_mem blocks;

    public ClBlockPalette(BlockPalette palette, ClTextureAtlas texMap) {
        RendererInstance instance = RendererInstance.get();

        List<Block> blockPalette = palette.getPalette();
        IntArrayList packedBlocks = new IntArrayList(blockPalette.size());

        for (Block block : blockPalette) {
            ClBlock clBlock = new ClBlock(block, texMap);
            packedBlocks.addAll(IntList.of(clBlock.pack()));
        }

        blocks = clCreateBuffer(instance.context, CL_MEM_READ_ONLY | CL_MEM_COPY_HOST_PTR,
                (long) Sizeof.cl_uint * packedBlocks.size(), Pointer.to(packedBlocks.toIntArray()),
                null);
    }

    public static void preLoad(BlockPalette palette, ClTextureAtlas.AtlasBuilder builder) {
        List<Block> blockPalette = palette.getPalette();

        for (Block block : blockPalette) {
            ClBlock.preLoad(block, builder);
        }
    }

    public void release() {
        clReleaseMemObject(blocks);
    }
}
