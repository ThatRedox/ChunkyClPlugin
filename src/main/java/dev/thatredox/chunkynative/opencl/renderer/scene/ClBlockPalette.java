package dev.thatredox.chunkynative.opencl.renderer.scene;

import dev.thatredox.chunkynative.common.export.AbstractTextureLoader;
import dev.thatredox.chunkynative.opencl.renderer.RendererInstance;
import static org.jocl.CL.*;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import org.jocl.*;

import java.util.List;

import se.llbit.chunky.block.Block;
import se.llbit.chunky.chunk.BlockPalette;

public class ClBlockPalette {
    public final cl_mem blocks;
    public final cl_mem quadsModels;
    public final cl_mem aabbModels;

    public ClBlockPalette(BlockPalette palette, AbstractTextureLoader texMap, ClMaterialPalette.Builder materialBuilder) {
        RendererInstance instance = RendererInstance.get();

        List<Block> blockPalette = palette.getPalette();
        IntArrayList packed = new IntArrayList(blockPalette.size());
        IntArrayList quads = new IntArrayList();
        IntArrayList aabbs = new IntArrayList();

        for (Block block : blockPalette) {
            ClBlock clBlock = new ClBlock(block, texMap, materialBuilder, quads, aabbs);
            packed.addAll(IntList.of(clBlock.pack()));
        }
        quads.add(0);
        aabbs.add(0);

        this.blocks = clCreateBuffer(instance.context, CL_MEM_READ_ONLY | CL_MEM_COPY_HOST_PTR,
                (long) Sizeof.cl_uint * packed.size(), Pointer.to(packed.toIntArray()),null);
        this.quadsModels = clCreateBuffer(instance.context, CL_MEM_READ_ONLY | CL_MEM_COPY_HOST_PTR,
                (long) Sizeof.cl_uint * quads.size(), Pointer.to(quads.toIntArray()), null);
        this.aabbModels = clCreateBuffer(instance.context, CL_MEM_READ_ONLY | CL_MEM_COPY_HOST_PTR,
                (long) Sizeof.cl_uint * aabbs.size(), Pointer.to(aabbs.toIntArray()), null);
    }

    public static void preLoad(BlockPalette palette, AbstractTextureLoader builder) {
        List<Block> blockPalette = palette.getPalette();

        for (Block block : blockPalette) {
            ClBlock.preLoad(block, builder);
        }
    }

    public void release() {
        clReleaseMemObject(blocks);
        clReleaseMemObject(quadsModels);
        clReleaseMemObject(aabbModels);
    }
}
