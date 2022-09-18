#ifndef CHUNKYCL_KERNEL_H
#define CHUNKYCL_KERNEL_H

#include "wavefront.h"
#include "octree.h"
#include "block.h"
#include "constants.h"
#include "randomness.h"
#include "bvh.h"
#include "sky.h"


bool closestIntersect(IntersectionRecord* record, Octree* octree, BlockPalette* palette, image2d_array_t atlas, int drawDepth, Bvh* worldBvh, Bvh* actorBvh) {
    bool hit = false;
    hit |= Octree_octreeIntersect(octree, record, palette, atlas, drawDepth);
    hit |= Bvh_intersect(worldBvh, record, atlas);
    hit |= Bvh_intersect(actorBvh, record, atlas);

    if (hit) {
        record->point = record->ray->origin + record->ray->direction * (record->distance - OFFSET);
    }
    return hit;
}

void intersectSky(IntersectionRecord* record, image2d_array_t atlas, Sun* sun, image2d_t skyTexture, float skyIntensity) {
    Sky_intersect(record, skyTexture, skyIntensity);
    Sun_intersect(sun, record, atlas);

    record->ray->pixel->color += (float3) (record->color.x, record->color.y, record->color.z) * record->ray->pixel->throughput * record->emittance;
}

void applyRayColor(IntersectionRecord* record, float emitterScale) {
    Ray* ray = record->ray;
    ray->origin = record->point;

    // Apply ray color
    float3 color = (float3) (record->color.x, record->color.y, record->color.z);
    ray->pixel->throughput *= color;

    float3 emittance = color;
    emittance *= record->emittance * emitterScale;
    ray->pixel->color += emittance * ray->pixel->throughput;
}

bool nextPath(IntersectionRecord* record, unsigned int *state, int maxDepth) {
    Ray* ray = record->ray;
    ray->origin = record->point;

    {
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

        if (fabs(record->normal.x) > 0.1) {
            xx = 0;
            xy = 1;
        } else {
            xx = 1;
            xy = 0;
        }
        xz = 0;

        ux = xy * record->normal.z - xz * record->normal.y;
        uy = xz * record->normal.x - xx * record->normal.z;
        uz = xx * record->normal.y - xy * record->normal.x;

        r = 1 / sqrt(ux*ux + uy*uy + uz*uz);

        ux *= r;
        uy *= r;
        uz *= r;

        vx = uy * record->normal.z - uz * record->normal.y;
        vy = uz * record->normal.x - ux * record->normal.z;
        vz = ux * record->normal.y - uy * record->normal.x;

        ray->direction.x = ux * tx + vx * ty + record->normal.x * tz;
        ray->direction.y = uy * tx + vy * ty + record->normal.y * tz;
        ray->direction.z = uz * tx + vz * ty + record->normal.z * tz;
    }

    ray->origin += ray->direction * OFFSET;
    ray->rayDepth += 1;
    record->distance = HUGE_VALF;
    return ray->rayDepth < maxDepth;
}

#endif
