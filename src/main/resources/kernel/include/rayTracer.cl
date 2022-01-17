#include "octree.h"
#include "wavefront.h"
#include "block.h"
#include "kernel.h"
#include "camera.h"

__kernel void render(__global const float *rayPos,
                     __global const float *rayDir,

                     __global const int *octreeDepth,
                     __global const int *octreeData,

                     __global const int *blockPalette,
                     __global const int *materialPalette,
                     __global const int *quadModels,
                     image2d_array_t textureAtlas,

                     image2d_t skyTexture,
                     __global const float* skyIntensity,

                     __global const int *randomSeed,
                     __global const int *bufferSpp,
                     __global float *res) {
    int gid = get_global_id(0);

    Pixel pixel = Pixel_new(gid);
    Ray ray = Ray_new(&pixel);
    IntersectionRecord record = IntersectionRecord_new(&ray);

    Octree octree = Octree_create(octreeData, *octreeDepth);
    BlockPalette palette = BlockPalette_new(blockPalette, materialPalette, quadModels);

    unsigned int randomState = *randomSeed + gid;
    unsigned int* state = &randomState;
    Random_nextState(state);

    // Set camera
    Camera_preGenerated(&ray, rayPos, rayDir);

    do {
        if (!closestIntersect(&record, &octree, &palette, textureAtlas, 256)) {
            intersectSky(&ray, skyTexture, *skyIntensity);
            break;
        }
    } while (nextPath(&record, state, 5, 13.0f));

    int spp = *bufferSpp;
    float3 bufferColor = vload3(gid, res);
    bufferColor = (bufferColor * spp + pixel.color) / (spp + 1);
    vstore3(bufferColor, gid, res);
}