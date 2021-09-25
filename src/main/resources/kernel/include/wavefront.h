#ifndef CHUNKYCLPLUGIN_WAVEFRONT_H
#define CHUNKYCLPLUGIN_WAVEFRONT_H

typedef struct {
    float3 origin;
    float3 direction;

    float3 point;
    float3 normal;

    float distance;
    int material;

    float3 color;
    float3 throughput;
    int pixelIndex;

    int rayDepth;
} wavefront_Ray;

#endif
