// This includes stuff regarding materials

#ifndef CHUNKYCLPLUGIN_MATERIAL_H
#define CHUNKYCLPLUGIN_MATERIAL_H

#include "../opencl.h"
#include "rt.h"
#include "textureAtlas.h"
#include "utils.h"
#include "constants.h"
#include "random.h"

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

typedef struct {
    float4 color;
    float emittance;
    float specular;
    float roughness;
} MaterialSample;

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

    // specular, metalness, roughness
    if (self.flags & 0b001) {
        float3 smr = Atlas_read_uv(uv.x, uv.y, self.specular_metalness_roughness, self.textureSize, atlas).xyz;
        sample->specular = smr.x;
        sample->roughness = smr.z;
    } else {
        sample->specular = (self.specular_metalness_roughness & 0xFF) / 255.0;
        sample->roughness = ((self.specular_metalness_roughness >> 16) & 0xFF) / 255.0;
    }
    
    return true;
}

float3 _Material_diffuseReflection(IntersectionRecord record, Random random) {
    float x1 = Random_nextFloat(random);
    float x2 = Random_nextFloat(random);
    float r = sqrt(x1);
    float theta = 2 * M_PI_F * x2;

    float tx = r * cos(theta);
    float ty = r * sin(theta);
    float tz = sqrt(1 - x1);

    // Transform from tangent space to world space
    float xx, xy, xz;
    float ux, uy, uz;
    float vx, vy, vz;

    if (fabs(record.normal.x) > 0.1) {
        xx = 0;
        xy = 1;
    } else {
        xx = 1;
        xy = 0;
    }
    xz = 0;

    ux = xy * record.normal.z - xz * record.normal.y;
    uy = xz * record.normal.x - xx * record.normal.z;
    uz = xx * record.normal.y - xy * record.normal.x;

    r = 1 / sqrt(ux*ux + uy*uy + uz*uz);

    ux *= r;
    uy *= r;
    uz *= r;

    vx = uy * record.normal.z - uz * record.normal.y;
    vy = uz * record.normal.x - ux * record.normal.z;
    vz = ux * record.normal.y - uy * record.normal.x;

    return (float3) (
        ux * tx + vx * ty + record.normal.x * tz,
        uy * tx + vy * ty + record.normal.y * tz,
        uz * tx + vz * ty + record.normal.z * tz
    );
}

float3 Material_samplePdf(Material self, IntersectionRecord record, MaterialSample sample, Ray ray, Random random) {
    if (sample.specular > 0 && sample.specular > Random_nextFloat(random)) {
        // Specular reflection
        float3 direction = ray.direction + (record.normal * (-2 * dot(ray.direction, record.normal)));

        if (sample.roughness > 0) {
            float3 diffuseDirection = _Material_diffuseReflection(record, random);
            diffuseDirection *= sample.roughness;
            direction = diffuseDirection + direction * (1 - sample.roughness);
        }

        if (signbit(dot(record.normal, direction)) == signbit(dot(record.normal, ray.direction))) {
            float factor = copysign(dot(record.normal, ray.direction), -EPS - dot(record.normal, direction));
            direction += factor * record.normal;
        }

        direction = normalize(direction);
        return direction;
    } else {
        // Diffuse reflection
        return _Material_diffuseReflection(record, random);
    }
}

#endif
