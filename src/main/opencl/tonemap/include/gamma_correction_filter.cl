#include "../opencl.h"
#include "double.h"
#include "rgba.h"

__kernel void filter(
        const int width,
        const int height,
        const float exposure,
        __global const imposter_double* input,
        __global unsigned int* res
) {
    int gid = get_global_id(0);
    int offset = gid * 3;

    float color_float[3];
    for (int i = 0; i < 3; i++) {
        color_float[i] = idouble_to_float(input[offset + i]);
    }
    float3 color = vload3(0, color_float);
    color *= exposure;
    color = pow(color, 1.0 / 2.2);

    float4 pixel;
    pixel.xyz = color;
    pixel.w = 1;
    res[gid] = color_to_argb(pixel);
}
