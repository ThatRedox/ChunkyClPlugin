#include "octree.h"
#include "wavefront.h"
#include "block.h"
#include "utils.h"

__kernel void render(__global const float *rayPos,
                     __global const float *rayDir,
                     __global const int *octreeDepth,
                     __global const int *octreeData,
                     __global const int *blockPalette,
                     image2d_array_t textureAtlas,
                     __global float *res) {
    int gid = get_global_id(0);

    Octree octree = Octree_create(octreeData, *octreeDepth);

    Ray wavefrontRay;
    Ray* ray = &wavefrontRay;

    ray->origin = vload3(gid, rayPos);
    ray->direction = vload3(gid, rayDir);
    ray->throughput = (float3) (1, 1, 1);

    if (Octree_octreeIntersect(&octree, ray, 256)) {
        Block block = Block_get(blockPalette, ray->material);
        float4 color = Block_getColor(block, ray, textureAtlas);

        ray->color = (float3) (color.x, color.y, color.z);
    } else {
        ray->color = (float3) (0, 0, 0);
    }

    vstore3(ray->color, gid, res);
}