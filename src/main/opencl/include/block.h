// This includes stuff regarding blocks and block palettes

#ifndef CHUNKYCLPLUGIN_BLOCK_H
#define CHUNKYCLPLUGIN_BLOCK_H

#include "../opencl.h"
#include "wavefront.h"
#include "utils.h"
#include "constants.h"
#include "textureAtlas.h"
#include "material.h"
#include "primitives.h"

typedef struct {
    __global const int* blockPalette;
    __global const int* quadModels;
    __global const int* aabbModels;
    MaterialPalette* materialPalette;
} BlockPalette;

BlockPalette BlockPalette_new(__global const int* blockPalette, __global const int* quadModels, __global const int* aabbModels, MaterialPalette* materialPalette) {
    BlockPalette p;
    p.blockPalette = blockPalette;
    p.quadModels = quadModels;
    p.aabbModels = aabbModels;
    p.materialPalette = materialPalette;
    return p;
}

float BlockPalette_intersectBlock(BlockPalette* self, int block, int3 blockPosition, IntersectionRecord* record, float3 origin, float3 direction, float3 invRayDir, image2d_array_t atlas) {
    // ANY_TYPE. Should not be intersected.
    if (block == 0x7FFFFFFE) {
        return NAN;
    }
    
    int modelType = self->blockPalette[block + 0];
    int modelPointer = self->blockPalette[block + 1];

    float3 normOrigin = (origin - direction * OFFSET) - int3toFloat3(blockPosition);
    float3 normal;
    float2 uv;

    switch (modelType) {
        default:
        case 0: {
            return NAN;
        }
        case 1: {
            Material mat = Material_get(self->materialPalette, modelPointer);

            AABB box = AABB_new(0, 1, 0, 1, 0, 1);
            float dist = AABB_full_intersect(&box, normOrigin, origin, invRayDir, &normal, &uv);

            // No intersection
            if (isnan(dist)) {
                return NAN;
            }

            record->normal = normal;
            if (Material_sample(&mat, atlas, record, uv)) {
                return dist - OFFSET;
            } else {
                return NAN;
            }
        }
        case 2: {
            bool hit = false;
            float dist = HUGE_VALF;
            int material;

            int boxes = self->aabbModels[modelPointer];
            for (int i = 0; i < boxes; i++) {
                int offset = modelPointer + 1 + i * TEX_AABB_SIZE;
                TexturedAABB box = TexturedAABB_new(self->aabbModels, offset);
                float t = TexturedAABB_intersect(&box, dist, normOrigin, record->ray->direction, invRayDir, &normal, &uv, &material);
                if (!isnan(t)) {
                    Material mat = Material_get(self->materialPalette, material);
                    if (Material_sample(&mat, atlas, record, uv)) {
                        record->normal = normal;
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
        case 3: {
            bool hit = false;
            float dist = HUGE_VALF;

            int quads = self->quadModels[modelPointer];
            for (int i = 0; i < quads; i++) {
                int offset = modelPointer + 1 + i * QUAD_SIZE;
                Quad q = Quad_new(self->quadModels, offset);
                float t = Quad_intersect(&q, dist, normOrigin, record->ray->direction, &normal, &uv);
                if (!isnan(t)) {
                    Material mat = Material_get(self->materialPalette, q.material);
                    if (Material_sample(&mat, atlas, record, uv)) {
                        record->normal = normal;
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
