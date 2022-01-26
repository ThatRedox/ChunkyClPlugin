#ifndef CHUNKYCL_TEXTURE_ATLAS_H
#define CHUNKYCL_TEXTURE_ATLAS_H

#include "constants.h"

// Image sampler for texture atlases.
const sampler_t Atlas_sampler = CLK_NORMALIZED_COORDS_FALSE | CLK_ADDRESS_CLAMP_TO_EDGE | CLK_FILTER_NEAREST;

float4 Atlas_read_xy(int x, int y, int location, image2d_array_t atlas) {
    x += ((location >> 22) & 0x1FF) * 16;
    y += ((location >> 13) & 0x1FF) * 16;
    int d = location & 0x7FFFF;

    return read_imagef(atlas, Atlas_sampler, (int4) (x, y, d, 0));
}

float4 Atlas_read_uv(float u, float v, int location, int size, image2d_array_t atlas) {
    int width = (size >> 16) & 0xFFFF;
    int height = size & 0xFFFF;

    v = (1 - v);

    int x = clamp((int) ((u - EPS) * width), 0, width-1);
    int y = clamp((int) ((v - EPS) * height), 0, height-1);

    return Atlas_read_xy(x, y, location, atlas);
}

#endif
