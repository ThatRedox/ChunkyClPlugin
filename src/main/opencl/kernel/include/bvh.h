#ifndef CHUNKYCL_PLUGIN_BVH
#define CHUNKYCL_PLUGIN_BVH

#include "../opencl.h"
#include "material.h"
#include "primitives.h"

typedef struct {
    __global const int* bvh;
    __global const int* trigs;
    MaterialPalette* materialPalette;
} Bvh;

Bvh Bvh_new(__global const int* bvh, __global const int* trigs, MaterialPalette* materialPalette) {
    Bvh b;
    b.bvh = bvh;
    b.trigs = trigs;
    b.materialPalette = materialPalette;
    return b;
}

bool Bvh_intersect(Bvh* self, IntersectionRecord* record, image2d_array_t atlas) {
    if (self->bvh[0] == 0) {
        if (isnan(as_float(self->bvh[1])) &&
            isnan(as_float(self->bvh[2])) &&
            isnan(as_float(self->bvh[3])) &&
            isnan(as_float(self->bvh[4])) &&
            isnan(as_float(self->bvh[5])) &&
            isnan(as_float(self->bvh[6]))) {
            return false;
        }
    }

    bool hit = false;
    
    int toVisit = 0;
    int currentNode = 0;
    int nodesToVisit[64];
    int node[7];
    AABB box;
    float3 invDir = 1 / record->ray->direction;

    float3 normal;
    float2 uv;
    int material;

    while (true) {
        node[0] = self->bvh[currentNode];

        if (node[0] <= 0) {
            // Is leaf
            int primIndex = -node[0];
            int numPrim = self->trigs[primIndex];

            for (int i = 0; i < numPrim; i++) {
                Triangle trig = Triangle_new(self->trigs, primIndex + 1 + TRIANGLE_SIZE * i);
                float dist = Triangle_intersect(&trig, record->distance, record->ray->origin, record->ray->direction, &normal, &uv, &material);
                
                if (!isnan(dist)) {
                    Material mat = Material_get(self->materialPalette, material);
                    if (Material_sample(&mat, atlas, record, uv)) {
                        record->normal = normal;
                        record->distance = dist;
                        hit = true;
                    }
                }
            }

            if (toVisit == 0) break;
            currentNode = nodesToVisit[--toVisit];
        } else {
            int offset = node[0];
            for (int i = 0; i < 7; i++) {
                node[i] = self->bvh[currentNode + 7 + i];
            }
            box = AABB_new(
                as_float(node[1]), as_float(node[2]),
                as_float(node[3]), as_float(node[4]),
                as_float(node[5]), as_float(node[6])
            );
            float t1 = AABB_quick_intersect(&box, record->ray->origin, invDir);

            for (int i = 0; i < 7; i++) {
                node[i] = self->bvh[offset + i];
            }
            box = AABB_new(
                as_float(node[1]), as_float(node[2]),
                as_float(node[3]), as_float(node[4]),
                as_float(node[5]), as_float(node[6])
            );
            float t2 = AABB_quick_intersect(&box, record->ray->origin, invDir);

            if (isnan(t1) || t1 > record->distance) {
                if (isnan(t2) || t2 > record->distance) {
                    if (toVisit == 0) break;
                    currentNode = nodesToVisit[--toVisit];
                } else {
                    currentNode = offset;
                }
            } else if (isnan(t2) || t2 > record->distance) {
                currentNode += 7;
            } else if (t1 < t2) {
                nodesToVisit[toVisit++] = offset;
                currentNode += 7;
            } else {
                nodesToVisit[toVisit++] = currentNode + 7;
                currentNode = offset;
            }
        }
    }

    return hit;
}

#endif
