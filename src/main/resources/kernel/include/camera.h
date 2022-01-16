#ifndef CHUNKYCLPLUGIN_CAMERA_H
#define CHUNKYCLPLUGIN_CAMERA_H

#include "wavefront.h"

void Camera_preGenerated(Ray* ray,
                         __global const float* rayPos,
                         __global const float* rayDir) {
    ray->pixel->throughput = (float3) (1, 1, 1);
    ray->origin = vload3(ray->pixel->index, rayPos);
    ray->direction = vload3(ray->pixel->index, rayDir);
}

#endif
