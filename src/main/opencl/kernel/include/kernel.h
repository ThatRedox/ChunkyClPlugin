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


bool closestIntersect(Scene self, image2d_array_t atlas, Ray ray, IntersectionRecord* record, MaterialSample* sample, Material* mat) {
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
        *mat = material;
        return true;
    }
    return false;
}

void intersectSky(image2d_t skyTexture, float skyIntensity, Sun sun, image2d_array_t atlas, Ray ray, MaterialSample* sample) {
    Sky_intersect(skyTexture, skyIntensity, ray, sample);
    Sun_intersect(sun, atlas, ray, sample);
}

#endif
