#ifndef CHUNKYCLPLUGIN_WAVEFRONT_H
#define CHUNKYCLPLUGIN_WAVEFRONT_H

#include "../opencl.h"

typedef struct {
    int index;

    float3 color;
    float3 throughput;
} Pixel;

Pixel Pixel_new(int index) {
    Pixel pixel;
    pixel.index = index;
    pixel.color = (float3) (0, 0, 0);
    pixel.throughput = (float3) (1, 1, 1);
    return pixel;
}

typedef struct {
    Pixel* pixel;

    float3 origin;
    float3 direction;
    int material;

    int rayDepth;
} Ray;

Ray Ray_new(Pixel* pixel) {
    Ray ray;
    ray.pixel = pixel;
    ray.material = 0;
    ray.rayDepth = 0;
    return ray;
}

typedef struct {
    Pixel* pixel;
    Ray* ray;

    float distance;
    int material;

    float3 normal;
    float3 point;

    float4 color;
    float emittance;
} IntersectionRecord;

IntersectionRecord IntersectionRecord_new(Ray* ray) {
    IntersectionRecord record;
    record.pixel = ray->pixel;
    record.ray = ray;

    record.distance = HUGE_VALF;
    record.material = 0;

    return record;
}

IntersectionRecord IntersectionRecord_copy(IntersectionRecord* other) {
    IntersectionRecord record;
    record.pixel = other->pixel;
    record.ray = other->ray;

    record.distance = other->distance;
    record.material = other->material;

    record.normal = other->normal;
    record.point = other->normal;

    record.color = other->color;
    record.emittance = other->emittance;
    return record;
}

#endif
