#ifndef CHUNKYCL_KERNEL_H
#define CHUNKYCL_KERNEL_H

#include "wavefront.h"
#include "octree.h"
#include "block.h"
#include "constants.h"
#include "randomness.h"

const sampler_t skySampler = CLK_NORMALIZED_COORDS_TRUE  | CLK_ADDRESS_CLAMP_TO_EDGE | CLK_FILTER_LINEAR;

bool extend(Ray* ray, Octree octree, int drawDepth) {
    bool hit = false;
    hit |= Octree_octreeIntersect(&octree, ray, drawDepth);

    ray->point -= ray->direction * OFFSET;
    return hit;
}

void intersectSky(Ray* ray, image2d_t skyTexture, float skyIntensity) {
    float theta = atan2(ray->direction.z, ray->direction.x);
    theta /= M_PI * 2;
    theta = fmod(fmod(theta, 1) + 1, 1);
    float phi = (asin(ray->direction.y) + M_PI_2) * M_1_PI_F;

    float4 skyColor = read_imagef(skyTexture, skySampler, (float2)(theta, phi));

    ray->color += (float3) (skyColor.x, skyColor.y, skyColor.z) * skyIntensity * ray->throughput;
}

bool nextPath(Ray* ray, __global const int *blockPalette, image2d_array_t textureAtlas, unsigned int *state, int maxDepth, float emitterScale) {
    ray->origin = ray->point;

    // Apply ray color
    Block block = Block_get(blockPalette, ray->material);
    float4 color = Block_getColor(block, textureAtlas, ray);
    ray->throughput *= (float3) (color.x, color.y, color.z);

    float3 emittance = (float3) (color.x, color.y, color.z);
    emittance *= Block_getEmittance(block, textureAtlas, ray);
    emittance *= emitterScale;
    ray->color += emittance * ray->throughput;

    // Diffuse reflection
    float x1 = Random_nextFloat(state);
    float x2 = Random_nextFloat(state);
    float r = sqrt(x1);
    float theta = 2 * M_PI_F * x2;

    float tx = r * cos(theta);
    float ty = r * sin(theta);
    float tz = sqrt(1 - x1);

    // Transform from tangent space to world space
    float xx, xy, xz;
    float ux, uy, uz;
    float vx, vy, vz;

    if (fabs(ray->normal.x) > 0.1) {
        xx = 0;
        xy = 1;
    } else {
        xx = 1;
        xy = 0;
    }
    xz = 0;

    ux = xy * ray->normal.x - xz * ray->normal.y;
    uy = xz * ray->normal.x - xx * ray->normal.z;
    uz = xx * ray->normal.y - xy * ray->normal.x;

    r = 1 / sqrt(ux*ux + uy*uy + uz*uz);

    ux *= r;
    uy *= r;
    uz *= r;

    vx = uy * ray->normal.z - uz * ray->normal.y;
    vy = uz * ray->normal.x - ux * ray->normal.z;
    vz = ux * ray->normal.y - uy * ray->normal.x;

    ray->direction.x = ux * tx + vx * ty + ray->normal.x * tz;
    ray->direction.y = uy * tx + vy * ty + ray->normal.y * tz;
    ray->direction.z = uz * tx + vz * ty + ray->normal.z * tz;

    ray->origin += ray->direction * OFFSET;

    ray->rayDepth += 1;
    return ray->rayDepth < maxDepth;
}

#endif
