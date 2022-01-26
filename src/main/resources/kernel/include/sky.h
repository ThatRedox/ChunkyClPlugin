#ifndef CHUNKYCLPLUGIN_SKY_H
#define CHUNKYCLPLUGIN_SKY_H

#include "wavefront.h"
#include "textureAtlas.h"

typedef struct {
    int flags;
    int textureSize;
    int texture;
    float intensity;
    float3 su;
    float3 sv;
    float3 sw;
} Sun;

Sun Sun_new(__global const int* data) {
    Sun sun;
    sun.flags = data[0];
    sun.textureSize = data[1];
    sun.texture = data[2];
    sun.intensity = as_float(data[3]);
    
    float phi = as_float(data[4]);
    float theta = as_float(data[5]);
    float r = fabs(cos(phi));
    
    sun.sw = (float3) (cos(theta) * r, sin(phi), sin(theta) * r);
    if (fabs(sun.sw.x) > 0.1f) {
        sun.su = (float3) (0, 1, 0);
    } else {
        sun.su = (float3) (1, 0, 0);
    }
    sun.sv = normalize(cross(sun.sw, sun.su));
    sun.su = cross(sun.sv, sun.sw);
    
    return sun;
}

bool Sun_intersect(Sun* self, IntersectionRecord* record, image2d_array_t atlas) {
    float3 direction = record->ray->direction;

    if (!(self->flags & 1) || dot(direction, self->sw) < 0.5f) {
        return false;
    }

    float radius = 0.03;

    float width = radius * 4;
    float width2 = width * 2;
    float a = M_PI_2_F - acos(dot(direction, self->su)) + width;
    if (a >= 0 && a < width2) {
        float b = M_PI_2_F - acos(dot(direction, self->sv)) + width;
        if (b >= 0 && b < width2) {
            float4 color = Atlas_read_uv(a / width2, b / width2, 
                                         self->texture, self->textureSize, atlas);
            color *= self->intensity * 10;
            record->color += color;
            return true;
        }
    }

    return false;
}

const sampler_t skySampler = CLK_NORMALIZED_COORDS_TRUE  | CLK_ADDRESS_CLAMP_TO_EDGE | CLK_FILTER_LINEAR;

void Sky_intersect(IntersectionRecord* record, image2d_t skyTexture, float skyIntensity) {
    float3 direction = record->ray->direction;

    float theta = atan2(direction.x, direction.y);
    theta /= M_PI_F * 2;
    theta = fmod(fmod(theta, 1) + 1, 1);
    float phi = (asin(direction.y) + M_PI_2_F) * M_1_PI_F;

    record->color = read_imagef(skyTexture, skySampler, (float2) (theta, phi)) * skyIntensity;
}

#endif
