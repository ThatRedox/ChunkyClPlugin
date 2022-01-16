// This includes stuff regarding blocks and block palettes

#ifndef CHUNKYCLPLUGIN_BLOCK_H
#define CHUNKYCLPLUGIN_BLOCK_H

#include "wavefront.h"
#include "utils.h"
#include "constants.h"
#include "textureAtlas.h"
#include "material.h"
#include "primitives.h"

typedef struct {
    Material mat;
} Block;

float Block_intersect(Block* block, int3 blockPosition, IntersectionRecord* record, float3 origin, float3 direction, float3 invRayDir, image2d_array_t atlas) {
    float3 normOrigin = (origin - direction * OFFSET) - int3toFloat3(blockPosition);
    float3 normal;
    float2 uv;

    // TODO: Implement custom block models
    AABB box = AABB_new(0, 1, 0, 1, 0, 1);
    float dist = AABB_full_intersect(&box, normOrigin, origin, invRayDir, &normal, &uv);

    // No intersection
    if (isnan(dist)) {
        return NAN;
    }

    record->normal = normal;
    Material_sample(&block->mat, atlas, record, uv);

    if (record->color.w < EPS) {
        return NAN;
    }

    return dist - OFFSET;
}

typedef struct {
    __global const int* palette;
//    __global const int* blocks;
} BlockPalette;

BlockPalette BlockPalette_new(__global const int* palette) {
    BlockPalette p;
    p.palette = palette;
    return p;
}

Block BlockPalette_get(BlockPalette* self, int block) {
    int offset = block * 6;
    Block b;
    b.mat = Material_get(self->palette, offset);
    return b;
}

#endif
