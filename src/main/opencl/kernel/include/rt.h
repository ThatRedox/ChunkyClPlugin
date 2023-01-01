#ifndef CHUNKYCLPLUGIN_WAVEFRONT_H
#define CHUNKYCLPLUGIN_WAVEFRONT_H

#include "../opencl.h"

typedef struct {
    float3 origin;
    float3 direction;
    int material;
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

typedef struct {
    float4 color;
    float emittance;
} MaterialSample;

#endif
