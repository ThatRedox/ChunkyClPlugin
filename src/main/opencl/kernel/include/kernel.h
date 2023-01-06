#ifndef CHUNKYCL_KERNEL_H
#define CHUNKYCL_KERNEL_H

#include "../opencl.h"
#include "rt.h"
#include "octree.h"
#include "block.h"
#include "constants.h"
#include "randomness.h"
#include "bvh.h"
#include "sky.h"


typedef struct {
    Octree octree;
    Bvh worldBvh;
    Bvh actorBvh;
    BlockPalette blockPalette;
    MaterialPalette materialPalette;
    int drawDepth;
} Scene;


bool closestIntersect(Scene self, image2d_array_t atlas, Ray ray, IntersectionRecord* record, MaterialSample* sample) {
    float distance = 0;

    for (int i = 0; i < self.drawDepth; i++) {
        IntersectionRecord tempRecord = *record;
        tempRecord.distance = record->distance - distance;

        Ray tempRay = ray;
        tempRay.origin += distance * tempRay.direction;

        if (tempRecord.distance <= 0) {
            return false;
        }

        bool hit = false;
        hit |= Octree_octreeIntersect(self.octree, self.blockPalette, self.drawDepth, ray, &tempRecord);
        hit |= Bvh_intersect(self.worldBvh, ray, &tempRecord);
        hit |= Bvh_intersect(self.actorBvh, ray, &tempRecord);

        if (!hit) {
            return false;
        }

        distance += tempRecord.distance;

        Material material = Material_get(self.materialPalette, tempRecord.material);
        if (!Material_sample(material, atlas, tempRecord.texCoord, sample)) {
            distance += OFFSET;
            continue;
        }

        *record = tempRecord;
        record->distance = distance;
        return true;
    }
    return false;
}

void intersectSky(image2d_t skyTexture, float skyIntensity, Sun sun, image2d_array_t atlas, Ray ray, MaterialSample* sample) {
    Sky_intersect(skyTexture, skyIntensity, ray, sample);
    Sun_intersect(sun, atlas, ray, sample);
}

Ray diffuseReflection(Ray ray, IntersectionRecord record, unsigned int *state) {
    Ray out = ray;

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

    if (fabs(record.normal.x) > 0.1) {
        xx = 0;
        xy = 1;
    } else {
        xx = 1;
        xy = 0;
    }
    xz = 0;

    ux = xy * record.normal.z - xz * record.normal.y;
    uy = xz * record.normal.x - xx * record.normal.z;
    uz = xx * record.normal.y - xy * record.normal.x;

    r = 1 / sqrt(ux*ux + uy*uy + uz*uz);

    ux *= r;
    uy *= r;
    uz *= r;

    vx = uy * record.normal.z - uz * record.normal.y;
    vy = uz * record.normal.x - ux * record.normal.z;
    vz = ux * record.normal.y - uy * record.normal.x;

    out.origin = ray.origin + ray.direction * (record.distance - OFFSET);

    out.direction.x = ux * tx + vx * ty + record.normal.x * tz;
    out.direction.y = uy * tx + vy * ty + record.normal.y * tz;
    out.direction.z = uz * tx + vz * ty + record.normal.z * tz;

    out.origin += out.direction * OFFSET;

    return out;
}

#endif
