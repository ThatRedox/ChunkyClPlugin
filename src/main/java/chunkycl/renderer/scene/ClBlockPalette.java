package chunkycl.renderer.scene;

import chunkycl.renderer.RendererInstance;
import static org.jocl.CL.*;
import org.jocl.*;

import java.util.List;

import se.llbit.chunky.block.Block;
import se.llbit.chunky.chunk.BlockPalette;

public class ClBlockPalette {
    public final cl_mem blocks;

    public ClBlockPalette(BlockPalette palette, ClTextureArray texMap) {
        RendererInstance instance = RendererInstance.get();

        List<Block> blockPalette = palette.getPalette();

        int[] packedBlocks = new int[blockPalette.size() * 6];

        int index = 0;
        for (Block block : blockPalette) {
            int flags = 0;

            int size = (1 << 16) | 1;
            int tint = 0;
            int color = 0;
            int normal_emittance = 0;
            int specular_metalness_roughness = 0;

            if (!block.invisible) {
                // Not invisible
                flags |= 0x80000000;

                // Only block texture is a texture
                flags |= 0b100;

                // No tint
                tint = 0;

                // Block texture
                color = texMap.addTexture(block.texture);
                size = (block.texture.getWidth() << 16) | block.texture.getHeight();

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
            packedBlocks[index++] = size;
            packedBlocks[index++] = tint;
            packedBlocks[index++] = color;
            packedBlocks[index++] = normal_emittance;
            packedBlocks[index++] = specular_metalness_roughness;
        }

        blocks = clCreateBuffer(instance.context, CL_MEM_READ_ONLY | CL_MEM_COPY_HOST_PTR,
                (long) Sizeof.cl_uint * packedBlocks.length, Pointer.to(packedBlocks),
                null);
    }

    public void release() {
        clReleaseMemObject(blocks);
    }
}
