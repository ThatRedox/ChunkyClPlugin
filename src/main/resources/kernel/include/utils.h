#ifndef CHUNKYCL_PLUGIN_UTILS_H
#define CHUNKYCL_PLUGIN_UTILS_H

float4 colorFromArgb(unsigned int argb) {
    float4 color;
    color.w = (argb >> 24) & 0xFF;
    color.x = (argb >> 16) & 0xFF;
    color.y = (argb >> 8) & 0xFF;
    color.z = argb & 0xFF;
    color /= 256.0f;
    return color;
}

#endif
