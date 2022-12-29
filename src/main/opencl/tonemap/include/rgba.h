#ifndef TONEMAP_RGBA_H
#define TONEMAP_RGBA_H

#include "../opencl.h"

unsigned int color_to_argb(float4 color) {
    color *= 255.0f;
    color += 0.5f;
    uint4 argb = (uint4) (
        color.x,
        color.y,
        color.z,
        color.w
    );
    argb = clamp(argb, (uint4) (0, 0, 0, 0), (uint4) (255, 255, 255, 255));
    return (argb.w << 24) | (argb.x << 16) | (argb.y << 8) | argb.z << 0;
}

#endif
