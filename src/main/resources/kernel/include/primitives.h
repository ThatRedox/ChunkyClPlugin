#ifndef CHUNKYCLPLUGIN_PRIMITIVES_H
#define CHUNKYCLPLUGIN_PRIMITIVES_H

#include "wavefront.h"

typedef struct {
    float xmin;
    float xmax;
    float ymin;
    float ymax;
    float zmin;
    float zmax;
} AABB;

AABB AABB_new(float xmin, float xmax, float ymin, float ymax, float zmin, float zmax) {
    AABB box;
    box.xmin = xmin;
    box.xmax = xmax;
    box.ymin = ymin;
    box.ymax = ymax;
    box.zmin = zmin;
    box.zmax = zmax;
    return box;
}

// Test for an intersection. Returns NaN if there was no intersection.
// Returns the distance if there was an intersection.
float AABB_quick_intersect(AABB* self, float3 origin, float3 invDir) {
    float3 minVals = (float3) (self->xmin, self->ymin, self->zmin);
    float3 maxVals = (float3) (self->xmax, self->ymax, self->zmax);

    float3 t1s = (minVals - origin) * invDir;
    float3 t2s = (maxVals - origin) * invDir;

    float3 tmins = fmin(t1s, t2s);
    float3 tmaxs = fmax(t1s, t2s);

    float tmin = fmax(tmins.x, fmax(tmins.y, tmins.z));
    float tmax = fmin(tmaxs.x, fmin(tmaxs.y, tmaxs.z));

    if (tmax < tmin) {
        return NAN;
    } else {
        return tmin;
    }
}


// Exit from an AABB
float AABB_exit(AABB* self, float3 origin, float3 invDir) {
    float3 minVals = (float3) (self->xmin, self->ymin, self->zmin);
    float3 maxVals = (float3) (self->xmax, self->ymax, self->zmax);

    float3 t1s = (minVals - origin) * invDir;
    float3 t2s = (maxVals - origin) * invDir;

    float3 tmaxs = fmax(t1s, t2s);
    return fmin(tmaxs.x, fmin(tmaxs.y, tmaxs.z));
}

// Test for an intersection and calculate the normal and UV.
// Returns NaN if there was no intersection. Returns the distance if
// there was an intersection.
float AABB_full_intersect(AABB* self, float3 origin, float3 dir, float3 invDir, float3* normal, float2* uv) {
    float3 minVals = (float3) (self->xmin, self->ymin, self->zmin);
    float3 maxVals = (float3) (self->xmax, self->ymax, self->zmax);

    float3 t1s = (minVals - origin) * invDir;
    float3 t2s = (maxVals - origin) * invDir;

    float3 tmins = fmin(t1s, t2s);
    float3 tmaxs = fmax(t1s, t2s);

    float tmin = fmax(tmins.x, fmax(tmins.y, tmins.z));
    float tmax = fmin(tmaxs.x, fmin(tmaxs.y, tmaxs.z));

    // No intersection
    if (tmax < tmin) {
        return NAN;
    }

    float3 o = origin + tmin * dir;
    float3 d = 1 / (maxVals - minVals);
    if (t1s.x == tmin) {
        *uv = (float2) (1 - (o.z - self->zmin) * d.z, (o.y - self->ymin) * d.y);
        *normal = (float3) (-1, 0, 0);
    }
    if (t2s.x == tmin) {
        *uv = (float2) ((o.z - self->zmin) * d.z, (o.y - self->ymin) * d.y);
        *normal = (float3) (1, 0, 0);
    }
    if (t1s.y == tmin) {
        *uv = (float2) ((o.x - self->xmin) * d.x, 1 - (o.z - self->zmin) * d.z);
        *normal = (float3) (0, -1, 0);
    }
    if (t2s.y == tmin) {
        *uv = (float2) ((o.x - self->xmin) * d.x, (o.z - self->zmin) * d.z);
        *normal = (float3) (0, 1, 0);
    }
    if (t1s.z == tmin) {
        *uv = (float2) ((o.x - self->xmin) * d.x, (o.y - self->ymin) * d.y);
        *normal = (float3) (0, 0, -1);
    }
    if (t2s.z == tmin) {
        *uv = (float2) (1 - (o.x - self->xmin) * d.x, (o.y - self->ymin) * d.y);
        *normal = (float3) (0, 0, 1);
    }

    return tmin;
}

#endif