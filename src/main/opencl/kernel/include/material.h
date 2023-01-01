// This includes stuff regarding materials

#ifndef CHUNKYCLPLUGIN_MATERIAL_H
#define CHUNKYCLPLUGIN_MATERIAL_H

#include "../opencl.h"
#include "rt.h"
#include "textureAtlas.h"
#include "utils.h"
#include "constants.h"

typedef struct {
    __global const int* palette;
} MaterialPalette;

MaterialPalette MaterialPalette_new(__global const int* palette) {
    MaterialPalette p;
    p.palette = palette;
    return p;
}

typedef struct {
    unsigned int flags;
    unsigned int tint;
    unsigned int textureSize;
    unsigned int color;
    unsigned int normal_emittance;
    unsigned int specular_metalness_roughness;
} Material;

Material Material_get(MaterialPalette self, int material) {
    Material m;
    m.flags = self.palette[material + 0];
    m.tint = self.palette[material + 1];
    m.textureSize = self.palette[material + 2];
    m.color = self.palette[material + 3];
    m.normal_emittance = self.palette[material + 4];
    m.specular_metalness_roughness = self.palette[material + 5];
    return m;
}

bool Material_sample(Material self, image2d_array_t atlas, float2 uv, MaterialSample* sample) {
    // Color
    float4 color;
    if (self.flags & 0b100)
        color = Atlas_read_uv(uv.x, uv.y, self.color, self.textureSize, atlas);
    else
        color = colorFromArgb(self.color);
    
    if (color.w > EPS) {
        sample->color = color;
    } else {
        return false;
    }

    // Tint
    switch (self.tint >> 24) {
        case 0xFF:
            sample->color *= colorFromArgb(self.tint);
            break;
        case 1:
            // TODO Proper foliage tint
            sample->color *= colorFromArgb(0xFF71A74D);
            break;
        case 2:
            // TODO Proper grass tint
            sample->color *= colorFromArgb(0xFF8EB971);
            break;
        case 3:
            // TODO Proper water tint
            sample->color *= colorFromArgb(0xFF3F76E4);
            break;
    }

    // (Normal) emittance
    if (self.flags & 0b010)
        sample->emittance = Atlas_read_uv(uv.x, uv.y, self.normal_emittance, self.textureSize, atlas).w;
    else
        sample->emittance = (self.normal_emittance & 0xFF) / 255.0;
    
    return true;
}

#endif
