#include "../opencl.h"
#include "octree.h"
#include "wavefront.h"
#include "block.h"
#include "material.h"
#include "kernel.h"
#include "camera.h"
#include "bvh.h"
#include "sky.h"

__kernel void render(
    __global const int* projectorType,
    __global const float* cameraSettings,

    __global const int* octreeDepth,
    __global const int* octreeData,

    __global const int* bPalette,
    __global const int* quadModels,
    __global const int* aabbModels,

    __global const int* worldBvhData,
    __global const int* actorBvhData,
    __global const int* bvhTrigs,

    image2d_array_t textureAtlas,
    __global const int* matPalette,

    image2d_t skyTexture,
    __global const float* skyIntensity,
    __global const int* sunData,

    __global const int* randomSeed,
    __global const int* bufferSpp,
    __global const int* width,
    __global const int* height,
    __global float* res

) {
    int gid = get_global_id(0);

    Pixel pixel = Pixel_new(gid);
    Ray ray = Ray_new(&pixel);
    IntersectionRecord record = IntersectionRecord_new(&ray);
    MaterialPalette materialPalette = MaterialPalette_new(matPalette);

    Octree octree = Octree_create(octreeData, *octreeDepth);
    Bvh worldBvh = Bvh_new(worldBvhData, bvhTrigs, &materialPalette);
    Bvh actorBvh = Bvh_new(actorBvhData, bvhTrigs, &materialPalette);

    BlockPalette blockPalette = BlockPalette_new(bPalette, quadModels, aabbModels, &materialPalette);

    Sun sun = Sun_new(sunData);

    unsigned int randomState = *randomSeed + gid;
    unsigned int* state = &randomState;
    Random_nextState(state);

    // Set camera
    if (*projectorType != -1) {
        float3 cameraPos = vload3(0, cameraSettings);
        float3 m1s = vload3(1, cameraSettings);
        float3 m2s = vload3(2, cameraSettings);
        float3 m3s = vload3(3, cameraSettings);

        float halfWidth = (*width) / (2.0 * (*height));
        float invHeight = 1.0 / (*height);
        float x = -halfWidth + ((pixel.index % (*width)) + Random_nextFloat(state)) * invHeight;
        float y = -0.5 + ((pixel.index / (*width)) + Random_nextFloat(state)) * invHeight;

        switch (*projectorType) {
            case 0:
                Camera_pinHole(x, y, state, &ray.origin, &ray.direction, cameraSettings+12);
                break;
        }

        ray.direction = (float3) (
            dot(m1s, ray.direction),
            dot(m2s, ray.direction),
            dot(m3s, ray.direction)
        );
        ray.origin = (float3) (
            dot(m1s, ray.origin),
            dot(m2s, ray.origin),
            dot(m3s, ray.origin)
        );

        ray.origin += cameraPos;
    } else {
        Camera_preGenerated(&ray, cameraSettings);
    }

    do {
        if (!closestIntersect(&record, &octree, &blockPalette, textureAtlas, 256, &worldBvh, &actorBvh)) {
            record.emittance = 1;
            intersectSky(&record, textureAtlas, &sun, skyTexture, *skyIntensity);
            break;
        }
        applyRayColor(&record, 13.0f);

        if (Sun_sampleDirection(&sun, &record, state)) {
            IntersectionRecord sampleRecord = IntersectionRecord_copy(&record);
            if (!closestIntersect(&sampleRecord, &octree, &blockPalette, textureAtlas, 256, &worldBvh, &actorBvh)) {
                intersectSky(&sampleRecord, textureAtlas, &sun, skyTexture, * skyIntensity);
            }
        }
    } while (nextPath(&record, state, 5));

    int spp = *bufferSpp;
    float3 bufferColor = vload3(gid, res);
    bufferColor = (bufferColor * spp + pixel.color) / (spp + 1);
    vstore3(bufferColor, gid, res);
}

__kernel void preview(
    __global const int* projectorType,
    __global const float* cameraSettings,

    __global const int* octreeDepth,
    __global const int* octreeData,

    __global const int* bPalette,
    __global const int* quadModels,
    __global const int* aabbModels,

    __global const int* worldBvhData,
    __global const int* actorBvhData,
    __global const int* bvhTrigs,

    image2d_array_t textureAtlas,
    __global const int* matPalette,

    image2d_t skyTexture,
    __global const float* skyIntensity,
    __global const int* sunData,

    __global const int* width,
    __global const int* height,
    __global int* res
) {
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
    Bvh worldBvh = Bvh_new(worldBvhData, bvhTrigs, &materialPalette);
    Bvh actorBvh = Bvh_new(actorBvhData, bvhTrigs, &materialPalette);

    BlockPalette blockPalette = BlockPalette_new(bPalette, quadModels, aabbModels, &materialPalette);

    Sun sun = Sun_new(sunData);

    unsigned int randomState = 0;
    unsigned int* state = &randomState;
    Random_nextState(state);

    // Set camera
    if (*projectorType != -1) {
        float3 cameraPos = vload3(0, cameraSettings);
        float3 m1s = vload3(1, cameraSettings);
        float3 m2s = vload3(2, cameraSettings);
        float3 m3s = vload3(3, cameraSettings);

        float halfWidth = (*width) / (2.0 * (*height));
        float invHeight = 1.0 / (*height);
        float x = -halfWidth + ((pixel.index % (*width)) + Random_nextFloat(state)) * invHeight;
        float y = -0.5 + ((pixel.index / (*width)) + Random_nextFloat(state)) * invHeight;

        switch (*projectorType) {
            case 0:
                Camera_pinHole(x, y, state, &ray.origin, &ray.direction, cameraSettings+12);
                break;
        }
        ray.direction = normalize(ray.direction);

        ray.direction = (float3) (
            dot(m1s, ray.direction),
            dot(m2s, ray.direction),
            dot(m3s, ray.direction)
        );
        ray.origin = (float3) (
            dot(m1s, ray.origin),
            dot(m2s, ray.origin),
            dot(m3s, ray.origin)
        );

        ray.origin += cameraPos;
    } else {
        Camera_preGenerated(&ray, cameraSettings);
    }

    if (closestIntersect(&record, &octree, &blockPalette, textureAtlas, 256, &worldBvh, &actorBvh)) {
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
