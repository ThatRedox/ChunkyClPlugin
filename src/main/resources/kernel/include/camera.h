#ifndef CHUNKYCLPLUGIN_CAMERA_H
#define CHUNKYCLPLUGIN_CAMERA_H

#include "wavefront.h"
#include "randomness.h"

void Camera_preGenerated(Ray* ray, __global const float* rays) {
    ray->origin = vload3(ray->pixel->index * 2, rays);
    ray->direction = vload3(ray->pixel->index * 2 + 1, rays);
}

void Camera_pinHole(float x, float y, unsigned int* state, float3* origin, float3* direction, __global const float* projectorSettings) {
    float aperature = projectorSettings[0];
    float subjectDistance = projectorSettings[1];
    float fovTan = projectorSettings[2];

    *origin = (float3) (0, 0, 0);
    *direction = (float3) (fovTan * x, fovTan * y, 1.0);

    if (aperature > 0) {
        *direction *= subjectDistance / direction->z;

        float r = sqrt(Random_nextFloat(state)) * aperature;
        float theta = Random_nextFloat(state) * M_PI_F * 2.0;
        float rx = cos(theta) * r;
        float ry = sin(theta) * r;

        *direction -= (float3) (rx, ry, 0);
        *origin += (float3) (rx, ry, 0);
    }
}

#endif
