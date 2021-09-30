#ifndef CHUNKYCLPLUGIN_BLOCK_H
#define CHUNKYCLPLUGIN_BLOCK_H

#include "wavefront.h"
#include "utils.h"
#include "constants.h"
#include "textureAtlas.h"

typedef struct {
    unsigned int flags;
    unsigned int textureSize;
    unsigned int tint;
    unsigned int color;
    unsigned int normal_emittance;
    unsigned int specular_metalness_roughness;
} Block;

Block Block_get(__global const int *palette, int block) {
    int offset = block * 6;
    Block b;
    b.flags = palette[offset];
    b.textureSize = palette[offset + 1];
    b.tint = palette[offset + 2];
    b.color = palette[offset + 3];
    b.normal_emittance = palette[offset + 4];
    b.specular_metalness_roughness = palette[offset+ 5];
    return b;
}

float4 Block_getColor(Block block, image2d_array_t atlas, Ray* ray) {
    if (block.flags & 0b100000) {
        return Atlas_read_uv(ray->uv.x, ray->uv.y, block.color, block.textureSize, atlas);
    } else {
        return colorFromArgb(block.color);
    }
}

float Block_getEmittance(Block block, image2d_array_t atlas, Ray* ray) {
    if (block.flags & 0b1000) {
        return Atlas_read_uv(ray->uv.x, ray->uv.y, block.normal_emittance, block.textureSize, atlas).w;
    } else {
        return (block.normal_emittance & 0xFF) / 255.0;
    }
}

#endif
