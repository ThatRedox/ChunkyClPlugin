// This includes stuff regarding materials

#ifndef CHUNKYCLPLUGIN_MATERIAL_H
#define CHUNKYCLPLUGIN_MATERIAL_H

#include "wavefront.h"
#include "textureAtlas.h"
#include "utils.h"

typedef struct {
    unsigned int flags;
    unsigned int textureSize;
    unsigned int tint;
    unsigned int color;
    unsigned int normal_emittance;
    unsigned int specular_metalness_roughness;
} Material;

Material Material_get(__global const int* data, int material) {
    int index = material * 6;
    Material m;
    m.flags = data[index + 0];
    m.textureSize = data[index + 1];
    m.tint = data[index + 2];
    m.color = data[index + 3];
    m.normal_emittance = data[index + 4];
    m.specular_metalness_roughness = data[index + 5];
    return m;
}

void Material_sample(Material* self, image2d_array_t atlas, IntersectionRecord* record, float2 uv) {
    // Color
    if (self->flags & 0b100)
        record->color = Atlas_read_uv(uv.x, uv.y, self->color, self->textureSize, atlas);
    else
        record->color = colorFromArgb(self->color);

    // Tint
    switch (self->tint >> 24) {
        case 0xFF:
            record->color *= colorFromArgb(self->tint);
            break;
        case 1:
            // TODO Proper foliage tint
            record->color *= colorFromArgb(0xFF71A74D);
            break;
        case 2:
            // TODO Proper grass tint
            record->color *= colorFromArgb(0xFF8EB971);
            break;
        case 3:
            // TODO Proper water tint
            record->color *= colorFromArgb(0xFF3F76E4);
            break;
    }

    // (Normal) emittance
    if (self->flags & 0b010)
        record->emittance = Atlas_read_uv(uv.x, uv.y, self->normal_emittance, self->textureSize, atlas).w;
    else
        record->emittance = (self->normal_emittance & 0xFF) / 255.0;
}

#endif
