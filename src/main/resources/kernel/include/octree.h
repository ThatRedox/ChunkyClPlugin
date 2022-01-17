#ifndef CHUNKYCLPLUGIN_OCTREE_H
#define CHUNKYCLPLUGIN_OCTREE_H

#include "wavefront.h"
#include "constants.h"
#include "primitives.h"
#include "block.h"
#include "utils.h"

typedef struct {
    __global const int* treeData;
    int depth;
} Octree;

Octree Octree_create(__global const int* treeData, int depth) {
    Octree octree;
    octree.treeData = treeData;
    octree.depth = depth;
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

bool Octree_octreeIntersect(Octree* self, IntersectionRecord* record, BlockPalette* palette, image2d_array_t atlas, int drawDepth) {
    Ray* ray = record->ray;

    float3 normalMarch = (float3) (0, 1, 0);
    float distMarch = 0;

    float3 invD = 1 / ray->direction;
    float3 offsetD = ray->direction * OFFSET;

    int depth = self->depth;

    for (int i = 0; i < drawDepth; i++) {
        if (distMarch > record->distance) {
            // Theres already been a closer intersection!
            return false;
        }

        float3 pos = ray->origin + (ray->direction * distMarch);
        int3 bp = intFloorFloat3(pos + offsetD);

        // Check inbounds
        int3 lv = bp >> depth;
        if ((lv.x != 0) | (lv.y != 0) | (lv.z != 0))
            return false;

        // Read the octree with depth
        int level = depth;
        int data = self->treeData[0];
        while (data > 0) {
            level--;
            lv = 1 & (bp >> level);
            data = self->treeData[data + ((lv.x << 2) | (lv.y << 1) | lv.z)];
        }
        data = -data;
        lv = bp >> level;

        // Get block data if there is an intersection
        if (data != ray->material) {
            float dist = BlockPalette_intersectBlock(palette, data, bp, record, pos, ray->direction, invD, atlas);

            if (!isnan(dist)) {
                record->distance = distMarch + dist;
                record->material = data;
                return true;
            }
        }

        // Exit the current leaf
        AABB box = AABB_new(lv.x << level, (lv.x + 1) << level,
                            lv.y << level, (lv.y + 1) << level,
                            lv.z << level, (lv.z + 1) << level);
        distMarch += AABB_exit(&box, pos, invD);
    }

    return false;
}

#endif
