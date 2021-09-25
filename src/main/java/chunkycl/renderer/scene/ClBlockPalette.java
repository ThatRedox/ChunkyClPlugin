package chunkycl.renderer.scene;

import static org.jocl.CL.*;

import chunkycl.renderer.RendererInstance;
import org.jocl.*;

import java.util.List;

import se.llbit.chunky.block.Block;
import se.llbit.chunky.chunk.BlockPalette;

public class ClBlockPalette {
    public final cl_mem blocks;

    public ClBlockPalette(BlockPalette palette) {
        RendererInstance instance = RendererInstance.get();

        List<Block> blockPalette = palette.getPalette();

        int[] packedBlocks = new int[blockPalette.size() * 6];

        int index = 0;
        for (Block block : blockPalette) {
            int flags = 0;

            int tint = 0;
            int color = 0;
            int normal_emittance = 0;
            int specular_metalness_roughness = 0;

            if (!block.invisible) {
                // Not invisible
                flags |= 0x80000000;

                // All attributes will be solid colors for now
                flags |= 0b000000;

                // No tint
                tint = 0;

                // Average color
                color = block.texture.getAvgColor();

                // No normal map
                // Block emittance
                normal_emittance = (int) (block.emittance * 255.0) << 24;

                // Block specular
                // Block metalness
                // Block roughness
                specular_metalness_roughness = (int) (block.specular * 255.0) |
                        ((int) (block.metalness * 255.0) << 8) |
                        ((int) (block.roughness * 255.0) << 16);
            }

            packedBlocks[index++] = flags;
            packedBlocks[index++] = 0;
            packedBlocks[index++] = tint;
            packedBlocks[index++] = color;
            packedBlocks[index++] = normal_emittance;
            packedBlocks[index++] = specular_metalness_roughness;
        }

        blocks = clCreateBuffer(instance.context, CL_MEM_READ_ONLY | CL_MEM_COPY_HOST_PTR,
                (long) Sizeof.cl_uint * packedBlocks.length, Pointer.to(packedBlocks),
                null);
    }
}
