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
            record.emittance = 1;
            intersectSky(&record, textureAtlas, &sun, skyTexture, *skyIntensity);
            break;
        }
        applyRayColor(&record, 13.0f);

        if (Sun_sampleDirection(&sun, &record, state)) {
            IntersectionRecord sampleRecord = IntersectionRecord_copy(&record);
            if (!closestIntersect(&sampleRecord, &octree, &blockPalette, textureAtlas, 256, &bvh)) {
                intersectSky(&sampleRecord, textureAtlas, &sun, skyTexture, * skyIntensity);
            }
        }
    } while (nextPath(&record, state, 5));

    int spp = *bufferSpp;
    float3 bufferColor = vload3(gid, res);
    bufferColor = (bufferColor * spp + pixel.color) / (spp + 1);
    vstore3(bufferColor, gid, res);
}

__kernel void preview(__global const float* rayPos,
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
                      __global const int* width,
                      __global const int* height,
                      __global int* res) {
    int gid = get_global_id(0);
    int px = gid % *width;
    int py = gid / *width;

    // Crosshairs?
    if ((px == *width / 2 && (py >= *height / 2 - 5 && py <= *height / 2 + 5)) ||
        (py == *height / 2 && (px >= *width / 2 - 5 && px <= *width / 2 + 5))) {
        res[gid] = 0xFFFFFFFF;
        return;
    }

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

    if (closestIntersect(&record, &octree, &blockPalette, textureAtlas, 256, &bvh)) {
        float shading = dot(record.normal, (float3) (0.25, 0.866, 0.433));
        shading = fmax(0.3f, shading);
        record.color *= shading;
    } else {
        record.emittance = 1;
        intersectSky(&record, textureAtlas, &sun, skyTexture, *skyIntensity);
    }

    record.color = sqrt(record.color);

    int3 rgb = intFloorFloat3(clamp(float4toFloat3(record.color) * 255.0f, 0.0f, 255.0f));
    res[gid] = 0xFF000000 | (rgb.x << 16) | (rgb.y << 8) | rgb.z;
}
