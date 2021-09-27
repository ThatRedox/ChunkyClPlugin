#ifndef CHUNKYCLPLUGIN_OCTREE_H
#define CHUNKYCLPLUGIN_OCTREE_H

#include "wavefront.h"
#include "constants.h"

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

bool Octree_octreeIntersect(Octree* octree, Ray* ray, int drawDepth) {
    float3 normalMarch = (float3) (0, 1, 0);
    float distMarch = 0;

    float3 invD = 1 / ray->direction;
    float3 offsetD = -(ray->origin) * invD;

    int depth = octree->depth;

    for (int i = 0; i < drawDepth; i++) {
        float3 pos = ray->origin + (ray->direction * distMarch);

        int x = floor(pos.x);
        int y = floor(pos.y);
        int z = floor(pos.z);

        // Check inbounds
        int lx = x >> depth;
        int ly = y >> depth;
        int lz = z >> depth;

        if ((lx != 0) | (ly != 0) | (lz != 0))
            return false;

        // Read with depth
        int nodeIndex = 0;
        int level = depth;
        int data = octree->treeData[nodeIndex];
        while (data > 0) {
            level--;
            lx = 1 & (x >> level);
            ly = 1 & (y >> level);
            lz = 1 & (z >> level);

            nodeIndex = data + ((lx << 2) | (ly << 1) | lz);
            data = octree->treeData[nodeIndex];
        }
        data = -data;

        lx = x >> level;
        ly = y >> level;
        lz = z >> level;

        // Get block data if there is an intersection
        if (data != 0) {
            // TODO: implement checking alpha and custom block model
            float3 bp = floor(pos);
            if (normalMarch.y != 0) {
                ray->uv = (float2) (pos.x - bp.x, pos.z - bp.z);
            } else if (normalMarch.x != 0) {
                ray->uv = (float2) (pos.z - bp.z, pos.y - bp.y);
            } else {
                ray->uv = (float2) (pos.x - bp.x,pos.y - bp.y);
            }
            if (normalMarch.x > 0 || normalMarch.z < 0) {
                ray->uv.x = 1 - ray->uv.x;
            }
            if (normalMarch.y > 0) {
                ray->uv.y = 1 - ray->uv.y;
            }

            ray->point = pos;
            ray->normal = normalMarch;
            ray->distance = distMarch;
            ray->material = data;
            return true;
        }

        // Exit the current leaf
        int nx = 0, ny = 0, nz = 0;
        float tNear = HUGE_VALF;

        float t = (lx << level) * invD.x + offsetD.x;
        if (t > distMarch + EPS) {
            tNear = t;
            nx = 1;
        }

        t = ((lx + 1) << level) * invD.x + offsetD.x;
        if (t < tNear && t > distMarch + EPS) {
            tNear = t;
            nx = -1;
        }

        t = (ly << level) * invD.y + offsetD.y;
        if (t < tNear && t > distMarch + EPS) {
            tNear = t;
            ny = 1;
            nx = 0;
        }

        t = ((ly + 1) << level) * invD.y + offsetD.y;
        if (t < tNear && t > distMarch + EPS) {
            tNear = t;
            ny = -1;
            nx = 0;
        }

        t = (lz << level) * invD.z + offsetD.z;
        if (t < tNear && t > distMarch + EPS) {
            tNear = t;
            nz = 1;
            ny = 0;
            nx = 0;
        }

        t = ((lz + 1) << level) * invD.z + offsetD.z;
        if (t < tNear && t > distMarch + EPS) {
            tNear = t;
            nz = -1;
            ny = 0;
            nx = 0;
        }

        normalMarch.x = nx;
        normalMarch.y = ny;
        normalMarch.z = nz;

        distMarch = tNear + OFFSET;
    }

    return false;
}

#endif
