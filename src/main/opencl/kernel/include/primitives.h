#ifndef CHUNKYCLPLUGIN_PRIMITIVES_H
#define CHUNKYCLPLUGIN_PRIMITIVES_H

#include "../opencl.h"
#include "rt.h"
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

/// Test if a point is inside the AABB
bool AABB_inside(AABB self, float3 point) {
    return
        (self.xmin <= point.x && point.x <= self.xmax) &&
        (self.ymin <= point.y && point.y <= self.ymax) &&
        (self.zmin <= point.z && point.z <= self.zmax);
}

// Test for an intersection. Returns NaN if there was no intersection.
// Returns the distance if there was an intersection.
float AABB_quick_intersect(AABB self, float3 origin, float3 invDir) {
    float3 minVals = (float3) (self.xmin, self.ymin, self.zmin);
    float3 maxVals = (float3) (self.xmax, self.ymax, self.zmax);

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
float AABB_exit(AABB self, float3 origin, float3 invDir) {
    float3 minVals = (float3) (self.xmin, self.ymin, self.zmin);
    float3 maxVals = (float3) (self.xmax, self.ymax, self.zmax);

    float3 t1s = (minVals - origin) * invDir;
    float3 t2s = (maxVals - origin) * invDir;

    float3 tmaxs = fmax(t1s, t2s);
    return fmin(tmaxs.x, fmin(tmaxs.y, tmaxs.z));
}

// Test for an intersection and calculate the normal and UV.
// Returns NaN if there was no intersection. Returns the distance if
// there was an intersection.
bool AABB_full_intersect(AABB self, Ray ray, IntersectionRecord* record) {
    float3 minVals = (float3) (self.xmin, self.ymin, self.zmin);
    float3 maxVals = (float3) (self.xmax, self.ymax, self.zmax);

    float3 t1s = (minVals - ray.origin) / ray.direction;
    float3 t2s = (maxVals - ray.origin) / ray.direction;

    float3 tmins = fmin(t1s, t2s);
    float3 tmaxs = fmax(t1s, t2s);

    float tmin = fmax(tmins.x, fmax(tmins.y, tmins.z));
    float tmax = fmin(tmaxs.x, fmin(tmaxs.y, tmaxs.z));

    // No intersection
    if (tmax < tmin) {
        return false;
    }
    // Intersection too far
    if (tmin >= record->distance) {
        return false;
    }

    record->distance = tmin;

    float3 o = ray.origin + tmin * ray.direction;
    float3 d = 1 / (maxVals - minVals);
    if (t1s.x == tmin) {
        record->texCoord = (float2) (1 - (o.z - self.zmin) * d.z, (o.y - self.ymin) * d.y);
        record->normal = (float3) (-1, 0, 0);
    }
    if (t2s.x == tmin) {
        record->texCoord = (float2) ((o.z - self.zmin) * d.z, (o.y - self.ymin) * d.y);
        record->normal = (float3) (1, 0, 0);
    }
    if (t1s.y == tmin) {
        record->texCoord = (float2) ((o.x - self.xmin) * d.x, 1 - (o.z - self.zmin) * d.z);
        record->normal = (float3) (0, -1, 0);
    }
    if (t2s.y == tmin) {
        record->texCoord = (float2) ((o.x - self.xmin) * d.x, (o.z - self.zmin) * d.z);
        record->normal = (float3) (0, 1, 0);
    }
    if (t1s.z == tmin) {
        record->texCoord = (float2) ((o.x - self.xmin) * d.x, (o.y - self.ymin) * d.y);
        record->normal = (float3) (0, 0, -1);
    }
    if (t2s.z == tmin) {
        record->texCoord = (float2) (1 - (o.x - self.xmin) * d.x, (o.y - self.ymin) * d.y);
        record->normal = (float3) (0, 0, 1);
    }

    return true;
}

// Test for an intersection and calculate the normal and UV.
// Returns NaN if there was no intersection. Returns the distance if
// there was an intersection. Uses full block uv mapping.
bool AABB_full_intersect_map_2(AABB self, Ray ray, IntersectionRecord* record) {
    float3 minVals = (float3) (self.xmin, self.ymin, self.zmin);
    float3 maxVals = (float3) (self.xmax, self.ymax, self.zmax);

    float3 t1s = (minVals - ray.origin) / ray.direction;
    float3 t2s = (maxVals - ray.origin) / ray.direction;

    float3 tmins = fmin(t1s, t2s);
    float3 tmaxs = fmax(t1s, t2s);

    float tmin = fmax(tmins.x, fmax(tmins.y, tmins.z));
    float tmax = fmin(tmaxs.x, fmin(tmaxs.y, tmaxs.z));

    // No intersection
    if (tmax < tmin) {
        return false;
    }
    // Intersection too far
    if (tmin >= record->distance) {
        return false;
    }

    record->distance = tmin;

    float3 o = ray.origin + tmin * ray.direction;
    if (t1s.x == tmin) {
        record->texCoord = (float2) (o.z, o.y);
        record->normal = (float3) (-1, 0, 0);
    }
    if (t2s.x == tmin) {
        record->texCoord = (float2) (1 - o.z, o.y);
        record->normal = (float3) (1, 0, 0);
    }
    if (t1s.y == tmin) {
        record->texCoord = (float2) (o.x, o.z);
        record->normal = (float3) (0, -1, 0);
    }
    if (t2s.y == tmin) {
        record->texCoord = (float2) (o.x, 1 - o.z);
        record->normal = (float3) (0, 1, 0);
    }
    if (t1s.z == tmin) {
        record->texCoord = (float2) (1 - o.x, o.y);
        record->normal = (float3) (0, 0, -1);
    }
    if (t2s.z == tmin) {
        record->texCoord = (float2) (o.x, o.y);
        record->normal = (float3) (0, 0, 1);
    }

    return true;
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

bool TexturedAABB_intersect(TexturedAABB self, Ray ray, IntersectionRecord* record) {
    IntersectionRecord tempRecord = *record;

    bool hit = AABB_full_intersect_map_2(self.box, ray, &tempRecord);
    if (!hit || tempRecord.distance < -EPS) {
        return false;
    }

    int flags = 0;
    if (tempRecord.normal.z == -1) {
        tempRecord.material = self.mn;
        flags = self.flags;
    }
    if (tempRecord.normal.x == 1) {
        tempRecord.material = self.me;
        flags = self.flags >> 4;
    }
    if (tempRecord.normal.z == -1) {
        tempRecord.material = self.ms;
        flags = self.flags >> 8;
    }
    if (tempRecord.normal.x == -1) {
        tempRecord.material = self.mw;
        flags = self.flags >> 12;
    }
    if (tempRecord.normal.y == 1) {
        tempRecord.material = self.mt;
        flags = self.flags >> 16;
    }
    if (tempRecord.normal.y == -1) {
        tempRecord.material = self.mb;
        flags = self.flags >> 20;
    }

    // No hit
    if (flags & 0b1000) {
        return false;
    }

    // Flip U
    if (flags & 0b0100) {
        tempRecord.texCoord.x = 1 - tempRecord.texCoord.x;
    }

    // Flip V
    if (flags & 0b0010) {
        tempRecord.texCoord.y = 1 - tempRecord.texCoord.y;
    }

    // Swap
    if (flags & 0b0001) {
        tempRecord.texCoord = tempRecord.texCoord.yx;
    }

    *record = tempRecord;
    return true;
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

bool Quad_intersect(Quad self, Ray ray, IntersectionRecord* record) {
    float3 n = normalize(cross(self.xv, self.yv));
    
    float denom = dot(ray.direction, n);
    if (denom < -EPS) {
        float t = -(dot(ray.origin, n) - dot(n, self.origin)) / denom;
        if (t > -EPS && t < record->distance) {
            float3 pt = ray.origin + ray.direction*t - self.origin;
            float u = dot(pt, self.xv) / dot(self.xv, self.xv);
            float v = dot(pt, self.yv) / dot(self.yv, self.yv);

            if (u >= 0 && u <= 1 && v >= 0 && v <= 1) {
                record->texCoord = (float2) (self.uv.x + (u * self.uv.y), self.uv.z + (v * self.uv.w));
                record->normal = n;
                record->distance = t;
                record->material = self.material;
                return true;
            }
        }
    }

    return false;
}

#define TRIANGLE_SIZE 20

typedef struct {
    int flags;
    float3 e1;
    float3 e2;
    float3 o;
    float3 n;
    float2 t1;
    float2 t2;
    float2 t3;
    int material;
} Triangle;

Triangle Triangle_new(__global const int* trigModels, int index) {
    Triangle t;
    t.flags = trigModels[index + 0];

    t.e1.x = as_float(trigModels[index + 1]);
    t.e1.y = as_float(trigModels[index + 2]);
    t.e1.z = as_float(trigModels[index + 3]);

    t.e2.x = as_float(trigModels[index + 4]);
    t.e2.y = as_float(trigModels[index + 5]);
    t.e2.z = as_float(trigModels[index + 6]);

    t.o.x = as_float(trigModels[index + 7]);
    t.o.y = as_float(trigModels[index + 8]);
    t.o.z = as_float(trigModels[index + 9]);

    t.n.x = as_float(trigModels[index + 10]);
    t.n.y = as_float(trigModels[index + 11]);
    t.n.z = as_float(trigModels[index + 12]);

    t.t1.x = as_float(trigModels[index + 13]);
    t.t1.y = as_float(trigModels[index + 14]);

    t.t2.x = as_float(trigModels[index + 15]);
    t.t2.y = as_float(trigModels[index + 16]);

    t.t3.x = as_float(trigModels[index + 17]);
    t.t3.y = as_float(trigModels[index + 18]);

    t.material = trigModels[index + 19];
    return t;
}

bool Triangle_intersect(Triangle self, Ray ray, IntersectionRecord* record) {
    float3 pvec, qvec, tvec;

    pvec = cross(ray.direction, self.e2);
    float det = dot(self.e1, pvec);
    if ((self.flags >> 8) & 1) {
        if (det > -EPS && det < EPS)
            return false;
    } else if (det > -EPS) {
        return false;
    }
    float recip = 1 / det;

    tvec = ray.origin - self.o;

    float u = dot(tvec, pvec) * recip;

    if (u < 0 || u > 1)
        return false;

    qvec = cross(tvec, self.e1);

    float v = dot(ray.direction, qvec) * recip;

    if (v < 0 || (u+v) > 1)
        return false;

    float t = dot(self.e2, qvec) * recip;

    if (t > EPS && t < record->distance) {
        float w = 1 - u - v;
        record->texCoord = (float2) (
            self.t1.x * u + self.t2.x * v + self.t3.x * w,
            self.t1.y * u + self.t2.y * v + self.t3.y * w
        );
        record->normal = self.n;
        record->material = self.material;
        record->distance = t;
        return true;
    }

    return false;
}

#endif