#include "octree.h"
#include "wavefront.h"
#include "block.h"
#include "utils.h"
#include "kernel.h"

__kernel void render(__global const float *rayPos,
                     __global const float *rayDir,
                     __global const int *octreeDepth,
                     __global const int *octreeData,
                     __global const int *blockPalette,
                     image2d_array_t textureAtlas,
                     __global const int *randomSeed,
                     __global float *res) {
    int gid = get_global_id(0);

    Octree octree = Octree_create(octreeData, *octreeDepth);
    Ray wavefrontRay = Ray_new();
    Ray* ray = &wavefrontRay;

    unsigned int randomState = *randomSeed + gid;
    unsigned int* state = &randomState;
    Random_nextState(state);

    // Set camera
    ray->origin = vload3(gid, rayPos);
    ray->direction = vload3(gid, rayDir);
    ray->throughput = (float3) (1, 1, 1);

    do {
        if (!extend(ray, octree, 256)) {
            break;
        }
    } while (nextPath(ray, blockPalette, textureAtlas, state, 5, 13.0f));

    vstore3(ray->color, gid, res);
}