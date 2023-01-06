#include "../opencl.h"
#include "octree.h"
#include "rt.h"
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

    Scene scene;
    scene.materialPalette = MaterialPalette_new(matPalette);
    scene.octree = Octree_create(octreeData, *octreeDepth);
    scene.worldBvh = Bvh_new(worldBvhData, bvhTrigs, &scene.materialPalette);
    scene.actorBvh = Bvh_new(actorBvhData, bvhTrigs, &scene.materialPalette);
    scene.blockPalette = BlockPalette_new(bPalette, quadModels, aabbModels, &scene.materialPalette);
    scene.drawDepth = 256;

    Sun sun = Sun_new(sunData);

    unsigned int randomState = *randomSeed + gid;
    unsigned int* state = &randomState;
    Random_nextState(state);

    // Set camera
    Ray ray;
    if (*projectorType != -1) {
        float3 cameraPos = vload3(0, cameraSettings);
        float3 m1s = vload3(1, cameraSettings);
        float3 m2s = vload3(2, cameraSettings);
        float3 m3s = vload3(3, cameraSettings);

        float halfWidth = (*width) / (2.0 * (*height));
        float invHeight = 1.0 / (*height);
        float x = -halfWidth + ((gid % (*width)) + Random_nextFloat(state)) * invHeight;
        float y = -0.5 + ((gid / (*width)) + Random_nextFloat(state)) * invHeight;

        switch (*projectorType) {
            case 0:
                ray = Camera_pinHole(x, y, state, cameraSettings+12);
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
        ray = Camera_preGenerated(cameraSettings, gid);
    }

    ray.material = 0;

    float3 color = (float3) (0.0);
    float3 throughput = (float3) (1.0);

    // TODO: Adjustable ray depth
    for (int depth = 0; depth < 5; depth++) {
        IntersectionRecord record = IntersectionRecord_new();
        MaterialSample sample;

        if (closestIntersect(scene, textureAtlas, ray, &record, &sample)) {
            throughput *= sample.color.xyz;
            color += sample.emittance * 13.0f * throughput;
            ray = diffuseReflection(ray, record, state);
        } else {
            intersectSky(skyTexture, *skyIntensity, sun, textureAtlas, ray, &sample);
            throughput *= sample.color.xyz;
            color += sample.emittance * throughput;
            break;
        }
    }

    int spp = *bufferSpp;
    float3 bufferColor = vload3(gid, res);
    bufferColor = (bufferColor * spp + color) / (spp + 1);
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

    Scene scene;
    scene.materialPalette = MaterialPalette_new(matPalette);
    scene.octree = Octree_create(octreeData, *octreeDepth);
    scene.worldBvh = Bvh_new(worldBvhData, bvhTrigs, &scene.materialPalette);
    scene.actorBvh = Bvh_new(actorBvhData, bvhTrigs, &scene.materialPalette);
    scene.blockPalette = BlockPalette_new(bPalette, quadModels, aabbModels, &scene.materialPalette);
    scene.drawDepth = 256;

    Sun sun = Sun_new(sunData);

    unsigned int randomState = 0;
    unsigned int* state = &randomState;
    Random_nextState(state);

    // Set camera
    Ray ray;
    if (*projectorType != -1) {
        float3 cameraPos = vload3(0, cameraSettings);
        float3 m1s = vload3(1, cameraSettings);
        float3 m2s = vload3(2, cameraSettings);
        float3 m3s = vload3(3, cameraSettings);

        float halfWidth = (*width) / (2.0 * (*height));
        float invHeight = 1.0 / (*height);
        float x = -halfWidth + ((gid % (*width)) + Random_nextFloat(state)) * invHeight;
        float y = -0.5 + ((gid / (*width)) + Random_nextFloat(state)) * invHeight;

        switch (*projectorType) {
            case 0:
                ray = Camera_pinHole(x, y, state, cameraSettings+12);
                break;
        }

        ray.direction = normalize((float3) (
            dot(m1s, ray.direction),
            dot(m2s, ray.direction),
            dot(m3s, ray.direction)
        ));
        ray.origin = (float3) (
            dot(m1s, ray.origin),
            dot(m2s, ray.origin),
            dot(m3s, ray.origin)
        );

        ray.origin += cameraPos;
    } else {
        ray = Camera_preGenerated(cameraSettings, gid);
    }

    IntersectionRecord record = IntersectionRecord_new();
    MaterialSample sample;
    ray.material = 0;

    float3 color = (float3) (0.0);

    if (closestIntersect(scene, textureAtlas, ray, &record, &sample)) {
        float shading = dot(record.normal, (float3) (0.25, 0.866, 0.433));
        shading = fmax(0.3f, shading);
        color = sample.color.xyz * shading;
    } else {
        intersectSky(skyTexture, *skyIntensity, sun, textureAtlas, ray, &sample);
        color = sample.color.xyz;
    }

    color = sqrt(color);
    int3 rgb = intFloorFloat3(clamp(color * 255.0f, 0.0f, 255.0f));
    res[gid] = 0xFF000000 | (rgb.x << 16) | (rgb.y << 8) | rgb.z;
}
