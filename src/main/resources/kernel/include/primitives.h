#ifndef CHUNKYCLPLUGIN_PRIMITIVES_H
#define CHUNKYCLPLUGIN_PRIMITIVES_H

#include "wavefront.h"
#include "constants.h"

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

// Test for an intersection and calculate the normal and UV.
// Returns NaN if there was no intersection. Returns the distance if
// there was an intersection. Uses full block uv mapping.
float AABB_full_intersect_map_2(AABB* self, float3 origin, float3 dir, float3 invDir, float3* normal, float2* uv) {
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
    if (t1s.x == tmin) {
        *uv = (float2) (o.z, o.y);
        *normal = (float3) (-1, 0, 0);
    }
    if (t2s.x == tmin) {
        *uv = (float2) (1 - o.z, o.y);
        *normal = (float3) (1, 0, 0);
    }
    if (t1s.y == tmin) {
        *uv = (float2) (o.x, o.z);
        *normal = (float3) (0, -1, 0);
    }
    if (t2s.y == tmin) {
        *uv = (float2) (o.x, 1 - o.z);
        *normal = (float3) (0, 1, 0);
    }
    if (t1s.z == tmin) {
        *uv = (float2) (1 - o.x, o.y);
        *normal = (float3) (0, 0, -1);
    }
    if (t2s.z == tmin) {
        *uv = (float2) (o.x, o.y);
        *normal = (float3) (0, 0, 1);
    }

    return tmin;
}


#define TEX_AABB_SIZE 13

typedef struct {
    AABB box;
    int mn;
    int me;
    int ms;
    int mw;
    int mt;
    int mb;
    int flags;
} TexturedAABB;

TexturedAABB TexturedAABB_new(__global const int* aabbModels, int index) {
    __global const int* model = aabbModels + index;

    TexturedAABB b;
    b.box = AABB_new(
        as_float(model[0]),
        as_float(model[1]),
        as_float(model[2]),
        as_float(model[3]),
        as_float(model[4]),
        as_float(model[5])
    );
    b.flags = model[6];
    b.mn = model[7];
    b.me = model[8];
    b.ms = model[9];
    b.mw = model[10];
    b.mt = model[11];
    b.mb = model[12];
    return b;
}

float TexturedAABB_intersect(TexturedAABB* self, float distance, float3 origin, float3 dir, float3 invDir, float3* normal, float2* uv, int* material) {
    float3 normal_temp;
    float2 uv_temp;

    float dist = AABB_full_intersect_map_2(&self->box, origin, dir, invDir, &normal_temp, &uv_temp);
    if (dist >= distance || dist < -EPS) {
        return NAN;
    }
    
    int mat;
    int flags = 0;
    if (normal_temp.z == -1) {
        mat = self->mn;
        flags = self->flags;
    }
    if (normal_temp.x == 1) {
        mat = self->me;
        flags = self->flags >> 4;
    }
    if (normal_temp.z == -1) {
        mat = self->ms;
        flags = self->flags >> 8;
    }
    if (normal_temp.x == -1) {
        mat = self->mw;
        flags = self->flags >> 12;
    }
    if (normal_temp.y == 1) {
        mat = self->mt;
        flags = self->flags >> 16;
    }
    if (normal_temp.y == -1) {
        mat = self->mb;
        flags = self->flags >> 20;
    }

    // No hit
    if (flags & 0b1000) {
        return NAN;
    }

    // Flip U
    if (flags & 0b0100) {
        uv_temp.x = 1 - uv_temp.x;
    }

    // Flip V
    if (flags & 0b0010) {
        uv_temp.y = 1 - uv_temp.y;
    }

    // Swap
    if (flags & 0b0001) {
        uv_temp = uv_temp.yx;
    }

    *material = mat;
    *normal = normal_temp;
    *uv = uv_temp;
    return dist;
}


#define QUAD_SIZE 15

typedef struct {
    float3 origin;
    float3 xv;
    float3 yv;
    float4 uv;
    int material;
    int flags;
} Quad;

Quad Quad_new(__global const int* quadModels, int index) {
    Quad q;
    q.origin.x = as_float(quadModels[index + 0]);
    q.origin.y = as_float(quadModels[index + 1]);
    q.origin.z = as_float(quadModels[index + 2]);

    q.xv.x = as_float(quadModels[index + 3]);
    q.xv.y = as_float(quadModels[index + 4]);
    q.xv.z = as_float(quadModels[index + 5]);

    q.yv.x = as_float(quadModels[index + 6]);
    q.yv.y = as_float(quadModels[index + 7]);
    q.yv.z = as_float(quadModels[index + 8]);

    q.uv.x = as_float(quadModels[index + 9]);
    q.uv.y = as_float(quadModels[index + 10]);
    q.uv.z = as_float(quadModels[index + 11]);
    q.uv.w = as_float(quadModels[index + 12]);

    q.material = quadModels[index + 13];
    q.flags = quadModels[index + 14];
    return q;
}

float Quad_intersect(Quad* self, float distance, float3 origin, float3 dir, float3* normal, float2* uv) {
    float3 n = normalize(cross(self->xv, self->yv));
    
    float denom = dot(dir, n);
    if (denom < -EPS) {
        float t = -(dot(origin, n) - dot(n, self->origin)) / denom;
        if (t > -EPS && t < distance) {
            float3 pt = origin + dir*t - self->origin;
            float u = dot(pt, self->xv) / dot(self->xv, self->xv);
            float v = dot(pt, self->yv) / dot(self->yv, self->yv);

            if (u >= 0 && u <= 1 && v >= 0 && v <= 1) {
                *uv = (float2) (self->uv.x + (u * self->uv.y), self->uv.z + (v * self->uv.w));
                *normal = n;

                return t;
            }
        }
    }

    return NAN;
}

#endif