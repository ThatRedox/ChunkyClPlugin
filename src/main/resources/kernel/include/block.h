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
    __global const int* blockPalette;
    __global const int* materialPalette;
    __global const int* quadModels;
} BlockPalette;

BlockPalette BlockPalette_new(__global const int* blockPalette, __global const int* materialPalette, __global const int* quadModels) {
    BlockPalette p;
    p.blockPalette = blockPalette;
    p.materialPalette = materialPalette;
    p.quadModels = quadModels;
    return p;
}

float BlockPalette_intersectBlock(BlockPalette* self, int block, int3 blockPosition, IntersectionRecord* record, float3 origin, float3 direction, float3 invRayDir, image2d_array_t atlas) {
    int offset = block * 2;
    int modelType = self->blockPalette[offset + 0];
    int modelPointer = self->blockPalette[offset + 1];

    float3 normOrigin = (origin - direction * OFFSET) - int3toFloat3(blockPosition);
    float3 normal;
    float2 uv;

    switch (modelType) {
        default:
        case 0: {
            Material mat = Material_get(self->materialPalette, modelPointer);

            AABB box = AABB_new(0, 1, 0, 1, 0, 1);
            float dist = AABB_full_intersect(&box, normOrigin, origin, invRayDir, &normal, &uv);

            // No intersection
            if (isnan(dist)) {
                return NAN;
            }

            record->normal = normal;
            Material_sample(&mat, atlas, record, uv);

            if (record->color.w < EPS) {
                return NAN;
            }

            return dist - OFFSET;
        }
        case 2: {
            bool hit = false;
            float dist = HUGE_VALF;

            int quads = self->quadModels[modelPointer];
            for (int i = 0; i < quads; i++) {
                int offset = modelPointer + 1 + i * QUAD_SIZE;
                Quad q = Quad_new(self->quadModels, offset);
                float t = Quad_intersect(&q, dist, normOrigin, record->ray->direction, &normal, &uv);
                if (!isnan(t)) {
                    record->normal = normal;
                    Material mat = Material_get(self->materialPalette, q.material);
                    Material_sample(&mat, atlas, record, uv);

                    if (record->color.w > EPS) {
                        dist = t;
                        hit = true;
                    }
                }
            }

            if (hit) {
                return dist;
            } else {
                return NAN;
            }
        }
    }
}

#endif
