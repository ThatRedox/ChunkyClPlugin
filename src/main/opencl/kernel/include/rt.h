#ifndef CHUNKYCLPLUGIN_WAVEFRONT_H
#define CHUNKYCLPLUGIN_WAVEFRONT_H

#include "../opencl.h"

#define RAY_INDIRECT 0b01
#define RAY_PREVIEW  0b10

typedef struct {
    float3 origin;
    float3 direction;
    int material;
    int flags;
} Ray;

typedef struct {
    float distance;
    int material;

    float3 normal;
    float2 texCoord;
} IntersectionRecord;

IntersectionRecord IntersectionRecord_new() {
    IntersectionRecord record;
    record.distance = HUGE_VALF;
    record.material = 0;
    record.normal = (float3) (0, 1, 0);
    return record;
}

#endif
