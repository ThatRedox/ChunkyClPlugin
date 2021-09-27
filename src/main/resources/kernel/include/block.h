#ifndef CHUNKYCLPLUGIN_BLOCK_H
#define CHUNKYCLPLUGIN_BLOCK_H

#include "wavefront.h"
#include "utils.h"
#include "imageArrays.h"
#include "constants.h"

typedef struct {
    unsigned int flags;
    unsigned int textureSize;
    unsigned int tint;
    unsigned int color;
    unsigned int normal_emittance;
    unsigned int specular_metalness_roughness;
} Block;

Block Block_get(__global const int* blockPalette, int block) {
    int offset = block * 6;
    Block b;
    b.flags = blockPalette[offset];
    b.textureSize = blockPalette[offset + 1];
    b.tint = blockPalette[offset + 2];
    b.color = blockPalette[offset + 3];
    b.normal_emittance = blockPalette[offset + 4];
    b.specular_metalness_roughness = blockPalette[offset+ 5];
    return b;
}

float4 Block_getColor(Block block, Ray* ray, image2d_t textureMap) {
    int argbColor;

    if (block.flags & 0b100) {
        int width = (block.textureSize >> 16) & 0xFFFF;
        int height = (block.textureSize) & 0xFFFF;

        int x = floor(ray->uv.x * width);
        int y = floor((1 - ray->uv.y) * height);

        int index = y * width + x;
        index += block.color;

        argbColor = indexu(textureMap, index);
    } else {
        argbColor = block.color;
    }

    return colorFromArgb(argbColor);
}

#endif
