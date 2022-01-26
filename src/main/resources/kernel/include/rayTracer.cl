#include "octree.h"
#include "wavefront.h"
#include "block.h"
#include "material.h"
#include "kernel.h"
#include "camera.h"
#include "bvh.h"
#include "sky.h"

__kernel void render(__global const float* rayPos,
                     __global const float* rayDir,

                     __global const int* octreeDepth,
                     __global const int* octreeData,

                     __global const int* bPalette,
                     __global const int* quadModels,
                     __global const int* aabbModels,

                     __global const int* bvhData,
                     __global const int* bvhTrigs,

                     image2d_array_t textureAtlas,
                     __global const int* matPalette,

                     image2d_t skyTexture,
                     __global const float* skyIntensity,
                     __global const int* sunData,

                     __global const int* randomSeed,
                     __global const int* bufferSpp,
                     __global float* res) {
    int gid = get_global_id(0);

    Pixel pixel = Pixel_new(gid);
    Ray ray = Ray_new(&pixel);
    IntersectionRecord record = IntersectionRecord_new(&ray);
    MaterialPalette materialPalette = MaterialPalette_new(matPalette);

    Octree octree = Octree_create(octreeData, *octreeDepth);
    Bvh bvh = Bvh_new(bvhData, bvhTrigs, &materialPalette);

    BlockPalette blockPalette = BlockPalette_new(bPalette, quadModels, aabbModels, &materialPalette);

    Sun sun = Sun_new(sunData);

    unsigned int randomState = *randomSeed + gid;
    unsigned int* state = &randomState;
    Random_nextState(state);

    // Set camera
    Camera_preGenerated(&ray, rayPos, rayDir);

    do {
        if (!closestIntersect(&record, &octree, &blockPalette, textureAtlas, 256, &bvh)) {
            intersectSky(&record, textureAtlas, &sun, skyTexture, *skyIntensity);
            break;
        }
    } while (nextPath(&record, state, 5, 13.0f));

    int spp = *bufferSpp;
    float3 bufferColor = vload3(gid, res);
    bufferColor = (bufferColor * spp + pixel.color) / (spp + 1);
    vstore3(bufferColor, gid, res);
}