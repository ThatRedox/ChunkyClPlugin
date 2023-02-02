// This includes stuff regarding blocks and block palettes

#ifndef CHUNKYCLPLUGIN_BLOCK_H
#define CHUNKYCLPLUGIN_BLOCK_H

#include "../opencl.h"
#include "rt.h"
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

bool BlockPalette_intersectNormalizedBlock(BlockPalette self, int block, int3 blockPosition, Ray ray, IntersectionRecord* record) {
    // ANY_TYPE. Should not be intersected.
    if (block == 0x7FFFFFFE) {
        return false;
    }
    
    int modelType = self.blockPalette[block + 0];
    int modelPointer = self.blockPalette[block + 1];

    bool hit = false;
    Ray tempRay = ray;
    tempRay.origin = ray.origin - int3toFloat3(blockPosition);

    switch (modelType) {
        default:
        case 0: {
            return false;
        }
        case 1: {
            AABB box = AABB_new(0, 1, 0, 1, 0, 1);
            hit = AABB_full_intersect(box, tempRay, record);
            record->material = modelPointer;
            break;
        }
        case 2: {
            int boxes = self.aabbModels[modelPointer];
            for (int i = 0; i < boxes; i++) {
                int offset = modelPointer + 1 + i * TEX_AABB_SIZE;
                TexturedAABB box = TexturedAABB_new(self.aabbModels, offset);
                hit |= TexturedAABB_intersect(box, tempRay, record);
            }
            break;
        }
        case 3: {
            int quads = self.quadModels[modelPointer];
            for (int i = 0; i < quads; i++) {
                int offset = modelPointer + 1 + i * QUAD_SIZE;
                Quad q = Quad_new(self.quadModels, offset);
                hit |= Quad_intersect(q, tempRay, record);
            }
            break;
        }
        case 4: {
            // Light block
            if (ray.flags & RAY_PREVIEW) {
                AABB box = AABB_new(0, 1, 0, 1, 0, 1);
                hit = AABB_full_intersect(box, tempRay, record);
                record->material = modelPointer;
            } else if (ray.flags & RAY_INDIRECT) {
                AABB box = AABB_new(0.125, 0.875, 0.125, 0.875, 0.125, 0.875);
                hit = AABB_full_intersect(box, tempRay, record);
                record->material = modelPointer;
            } else {
                return false;
            }
            break;
        }
    }

    return hit;
}

#endif
