#ifndef CHUNKYCLPLUGIN_WAVEFRONT_H
#define CHUNKYCLPLUGIN_WAVEFRONT_H

typedef struct {
    float3 origin;
    float3 direction;

    float3 point;
    float3 normal;
    float2 uv;

    float distance;
    int material;

    float3 color;
    float3 throughput;
    int pixelIndex;

    int rayDepth;
} Ray;

Ray Ray_new() {
    Ray ray;
    ray.throughput = (float3) (1, 1, 1);
    ray.color = (float3) (0, 0, 0);
    ray.distance = HUGE_VALF;
    ray.rayDepth = 0;
    return ray;
}

#endif
