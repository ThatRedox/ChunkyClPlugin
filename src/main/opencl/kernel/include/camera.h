#ifndef CHUNKYCLPLUGIN_CAMERA_H
#define CHUNKYCLPLUGIN_CAMERA_H

#include "../opencl.h"
#include "rt.h"
#include "random.h"

Ray Camera_preGenerated(__global const float* rays, int index) {
    Ray ray;
    ray.origin = vload3(index * 2, rays);
    ray.direction = vload3(index * 2 + 1, rays);
    return ray;
}

Ray Camera_pinHole(float x, float y, Random random, __global const float* projectorSettings) {
    Ray ray;
    float aperature = projectorSettings[0];
    float subjectDistance = projectorSettings[1];
    float fovTan = projectorSettings[2];

    ray.origin = (float3) (0, 0, 0);
    ray.direction = (float3) (fovTan * x, fovTan * y, 1.0);

    if (aperature > 0) {
        ray.direction *= subjectDistance / ray.direction.z;

        float r = sqrt(Random_nextFloat(random)) * aperature;
        float theta = Random_nextFloat(random) * M_PI_F * 2.0;
        float rx = cos(theta) * r;
        float ry = sin(theta) * r;

        ray.direction -= (float3) (rx, ry, 0);
        ray.origin += (float3) (rx, ry, 0);
    }

    return ray;
}

#endif
