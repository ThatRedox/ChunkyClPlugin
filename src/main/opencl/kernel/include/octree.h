#ifndef CHUNKYCLPLUGIN_OCTREE_H
#define CHUNKYCLPLUGIN_OCTREE_H

#include "../opencl.h"
#include "rt.h"
#include "constants.h"
#include "primitives.h"
#include "block.h"
#include "utils.h"

typedef struct {
    __global const int* treeData;
    AABB bounds;
    int depth;
} Octree;

Octree Octree_create(__global const int* treeData, int depth) {
    Octree octree;
    octree.treeData = treeData;
    octree.depth = depth;
    octree.bounds = AABB_new(0, 1<<depth, 0, 1<<depth, 0, 1<<depth);
    return octree;
}

int Octree_get(Octree* self, int x, int y, int z) {
    int3 bp = (int3) (x, y, z);

    // Check inbounds
    int3 lv = bp >> self->depth;
    if ((lv.x != 0) | (lv.y != 0) | (lv.z != 0))
        return 0;

    int level = self->depth;
    int data = self->treeData[0];
    while (data > 0) {
        level--;
        lv = 1 & (bp >> level);
        data = self->treeData[data + ((lv.x << 2) | (lv.y << 1) | lv.z)];
    }
    return -data;
}

bool Octree_octreeIntersect(Octree self, BlockPalette palette, int drawDepth, Ray ray, IntersectionRecord* record) {
    float distMarch = 0;

    float3 invD = 1 / ray.direction;
    float3 offsetD = ray.direction * OFFSET;

    int depth = self.depth;

    // Check if we are in bounds
    if (!AABB_inside(self.bounds, ray.origin)) {
        // Attempt to intersect with the octree
        float dist = AABB_quick_intersect(self.bounds, ray.origin, invD);
        if (isnan(dist) || dist < 0) {
            return false;
        } else {
            distMarch += dist + OFFSET;
        }
    }

    for (int i = 0; i < drawDepth; i++) {
        if (distMarch > record->distance) {
            // There's already been a closer intersection!
            return false;
        }

        float3 pos = ray.origin + ray.direction * distMarch;
        int3 bp = intFloorFloat3(pos + offsetD);

        // Check inbounds
        int3 lv = bp >> depth;
        if (lv.x != 0 || lv.y != 0 || lv.z != 0) {
            return false;
        }

        // Read the octree with depth
        int level = depth;
        int data = self.treeData[0];
        while (data > 0) {
            level--;
            lv = 1 & (bp >> level);
            data = self.treeData[data + ((lv.x << 2) | (lv.y << 1) | lv.z)];
        }
        data = -data;
        lv = bp >> level;

        // Get block data if there is an intersection
        if (data != ray.material) {
            if (BlockPalette_intersectNormalizedBlock(palette, data, bp, ray, record)) {
                return true;
            }
        }

        // Exit the current leaf
        AABB box = AABB_new(lv.x << level, (lv.x + 1) << level,
                            lv.y << level, (lv.y + 1) << level,
                            lv.z << level, (lv.z + 1) << level);
        distMarch += AABB_exit(box, pos + offsetD, invD) + OFFSET;
    }
    return false;
}

#endif
