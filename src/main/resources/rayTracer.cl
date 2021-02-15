#define EPS 0.000005    // Ray epsilon and exit offset
#define OFFSET 0.0001   // TODO: refine these values?

float dot3(float a[3], float b[3]);
void cross3(float a[3], float b[3], float r[3]);
void normalize3(float a[3]);

void calcSkyRay(float direction[3], float color[3], float e[3], image2d_t skyTexture, float sunPos[3], float sunIntensity, image2d_t textures, int sunIndex);
void sunIntersect(float direction[3], float color[3], float e[3], float sunPos[3], image2d_t textures, int sunIndex);

void getTextureRay(float color[3], float o[3], float n[3], float e[3], int block, image2d_t textures, image1d_t blockData, image2d_t grassTextures, image2d_t foliageTextures, int bounds);
int intersect(image2d_t octreeData, int depth, int x, int y, int z, __global const int *transparent, int transparentLength);
int octreeGet(int x, int y, int z, int bounds, image2d_t treeData);
int octreeRead(int index, image2d_t treeData);
int inbounds(float o[3], int bounds);
void exitBlock(float o[3], float d[3], float n[3], float *distance);
void diffuseReflect(float d[3], float o[3], float n[3], unsigned int *state);
int entityIntersect(float color[3], float o[3], float d[3], float n[3], float e[3], float* tbest, image2d_t entityData, image2d_t entityTrigs, image2d_t entityTextures);
int texturedTriangle(float color[3], float ro[3], float rd[3], float rn[3], float re[3], float* tbest, int index, image2d_t entityTrigs, image2d_t entityTextures);
float aabbIntersect(float o[3], float d[3], float xmin, float xmax, float ymin, float ymax, float zmin, float zmax);
int aabbInside(float o[3], float xmin, float xmax, float ymin, float ymax, float zmin, float zmax);
float indexf(image2d_t img, int index);
int indexi(image2d_t img, int index);
unsigned int indexu(image2d_t img, int index);

const sampler_t indexSampler = CLK_NORMALIZED_COORDS_FALSE | CLK_ADDRESS_CLAMP_TO_EDGE | CLK_FILTER_NEAREST;

// Randomness
void xorshift(unsigned int *state);
float nextFloat(unsigned int *state);

// Ray tracer entrypoint
__kernel void rayTracer(__global const float *rayPos,
                        __global const float *rayDir,
                        __global const int *depth,
                        image2d_t octreeData,
                        __global const int *voxelLength,
                        __global const int *transparent,
                        __global const int *transparentLength,
                        image2d_t textures,
                        image1d_t blockData,
                        __global const int *seed,
                        __global const int *rayDepth,
                        __global const int *preview,
                        __global const float *sunPos,
                        __global const int *sunIndex,
                        __global const float *sunIntensity,
                        image2d_t skyTexture,
                        image2d_t grassTextures,
                        image2d_t foliageTextures,
                        image2d_t entityData,
                        image2d_t entityTrigs,
                        image2d_t entityTextures,
                        __global const int *drawDepth,
                        __global float *res)
{
    int gid = get_global_id(0);

    // Initialize rng
    unsigned int rngState = *seed + gid;
    unsigned int *random = &rngState;
    xorshift(random);
    xorshift(random);

    // Ray origin
    float o[3];
    o[0] = rayPos[0];
    o[1] = rayPos[1];
    o[2] = rayPos[2];

    // Ray direction
    float d[3];
    d[0] = rayDir[gid*3 + 0];
    d[1] = rayDir[gid*3 + 1];
    d[2] = rayDir[gid*3 + 2];

    // Ray normal
    float n[3] = {0};

    // Junk array
    float junk[3];

    // Jitter each ray randomly
    // TODO: Pass the jitter amount as an argument?
    junk[0] = rayDir[gid*3 + 3] - d[0];
    junk[1] = rayDir[gid*3 + 4] - d[1];
    junk[2] = rayDir[gid*3 + 5] - d[2];

    float jitter = sqrt(junk[0]*junk[0] + junk[1]*junk[1] + junk[2]*junk[2])/2.0;

    d[0] += nextFloat(random) * jitter;
    d[1] += nextFloat(random) * jitter;
    d[2] += nextFloat(random) * jitter;

    normalize3(d);

    // Cap max bounces at 23 since no dynamic memory allocation
    int maxbounces = *rayDepth;
    if (maxbounces > 23) maxbounces = 23;

    // Ray bounce data stacks
    float colorStack[3 * 24] = {0};
    float emittanceStack[3 * 24] = {0};

    // Do the bounces
    for (int bounces = 0; bounces < maxbounces; bounces++)
    {
        float os[3] = {o[0], o[1], o[2]};
        float distance = 0;
        float color[3];
        float e[3] = {0};
        int hit = 0;

        // Ray march
        for (int i = 0; i < *drawDepth; i++) {
            if (!intersect(octreeData, *depth, o[0], o[1], o[2], transparent, *transparentLength))
                exitBlock(o, d, n, &distance);
            else {
                hit = 1;
                break;
            }

            if (!inbounds(o, *depth))
                break;
        }

        // Set color to sky color or texture color
        if (hit) {
            getTextureRay(color, o, n, e, octreeGet(o[0], o[1], o[2], *depth, octreeData), textures, blockData, grassTextures, foliageTextures, 1 << *depth);
        } else {
            float sunPosCopy[3] = {sunPos[0], sunPos[1], sunPos[2]};
            calcSkyRay(d, color, e, skyTexture, sunPosCopy, *sunIntensity, textures, *sunIndex);
        }

        // BVH intersection
        if (entityIntersect(color, os, d, n, e, &distance, entityData, entityTrigs, entityTextures)) {
            hit = 1;

            os[0] += d[0] * distance;
            os[1] += d[1] * distance;
            os[2] += d[2] * distance;

            o[0] = os[0];
            o[1] = os[1];
            o[2] = os[2];
        }

        // Exit on sky hit
        if (!hit) {
            float sunScale = pow(*sunIntensity, 2.2f);

            emittanceStack[bounces*3 + 0] = color[0] * sunScale;
            emittanceStack[bounces*3 + 1] = color[1] * sunScale;
            emittanceStack[bounces*3 + 2] = color[2] * sunScale;

            emittanceStack[bounces*3 + 3] = color[0] * sunScale;
            emittanceStack[bounces*3 + 4] = color[1] * sunScale;
            emittanceStack[bounces*3 + 5] = color[2] * sunScale;

            // Set color stack to sky color
            for (int i = bounces; i < maxbounces; i++) {
                colorStack[bounces*3 + 0] = color[0];
                colorStack[bounces*3 + 1] = color[1];
                colorStack[bounces*3 + 2] = color[2];
            }

            break;
        }

        // Add color and emittance to proper stacks
        colorStack[bounces*3 + 0] = color[0];
        colorStack[bounces*3 + 1] = color[1];
        colorStack[bounces*3 + 2] = color[2];

        emittanceStack[bounces*3 + 0] = e[0];
        emittanceStack[bounces*3 + 1] = e[1];
        emittanceStack[bounces*3 + 2] = e[2];

        // Calculate new diffuse reflection ray
        // TODO: Implement specular reflection
        diffuseReflect(d, o, n, random);
        exitBlock(o, d, junk, &distance);
    }

    if (*preview) {
        // preview shading = first intersect color * sun&ambient shading
        float shading = n[0] * 0.25 + n[1]*0.866 + n[2]*0.433;
        if (shading < 0.3) shading = 0.3;

        colorStack[0] *= shading;
        colorStack[1] *= shading;
        colorStack[2] *= shading;
    } else {
        // rendering shading = accumulate over all bounces
        // TODO: implement specular shading
        for (int i = maxbounces - 1; i >= 0; i--) {
            colorStack[i*3 + 0] *= colorStack[i*3 + 3] + emittanceStack[i*3 + 3];
            colorStack[i*3 + 1] *= colorStack[i*3 + 4] + emittanceStack[i*3 + 4];
            colorStack[i*3 + 2] *= colorStack[i*3 + 5] + emittanceStack[i*3 + 5];
        }
    }

    res[gid*3 + 0] = colorStack[0] * (emittanceStack[0] + 1);
    res[gid*3 + 1] = colorStack[1] * (emittanceStack[1] + 1);
    res[gid*3 + 2] = colorStack[2] * (emittanceStack[2] + 1);
}

// Xorshift random number generator based on
// https://en.wikipedia.org/wiki/Xorshift
void xorshift(unsigned int *state) {
    *state ^= *state << 13;
    *state ^= *state >> 7;
    *state ^= *state << 17;
    *state *= 0x5DEECE66D;
}

// Calculate the next float using the formula on
// https://docs.oracle.com/javase/8/docs/api/java/util/Random.html#nextFloat--
float nextFloat(unsigned int *state) {
    xorshift(state);

    return (*state >> 8) / ((float) (1 << 24));
}

int entityIntersect(float color[3], float o[3], float d[3], float n[3], float e[3], float* tbest, image2d_t entityData, image2d_t entityTrigs, image2d_t entityTextures) {
    float xmin, xmax, ymin, ymax, zmin, zmax;
    float size = indexf(entityData, 0);
    int hit = 0;

    for (int i = 0; i < size; i++) {
        int data = i*7 + 1;

        xmin = indexf(entityData, data+1);
        xmax = indexf(entityData, data+2);
        ymin = indexf(entityData, data+3);
        ymax = indexf(entityData, data+4);
        zmin = indexf(entityData, data+5);
        zmax = indexf(entityData, data+6);

        if (aabbInside(o, xmin, xmax, ymin, ymax, zmin, zmax) ||
            aabbIntersect(o, d, xmin, xmax, ymin, ymax, zmin, zmax)) {
            data = indexf(entityData, data);
            int length = indexf(entityTrigs, data);
            data += 1;

            for (int j = 0; j < length; j++) {
                int index = data + j * 30;
                switch ((int) indexf(entityTrigs, index)) {
                    case 0:
                        hit = texturedTriangle(color, o, d, n, e, tbest, index, entityTrigs, entityTextures) || hit;
                        break;
                }
            }
        }
    }

    return hit;
}

// Generate a diffuse reflection ray. Based on chunky code
void diffuseReflect(float d[3], float o[3], float n[3], unsigned int *state) {
    float x1 = nextFloat(state);
    float x2 = nextFloat(state);
    float r = sqrt(x1);
    float theta = 2 * M_PI * x2;

    float tx = r * cos(theta);
    float ty = r * sin(theta);
    float tz = sqrt(1 - x1);

    // transform from tangent space to world space
    float xx, xy, xz;
    float ux, uy, uz;
    float vx, vy, vz;

    if (fabs(n[0]) > .1) {
      xx = 0;
      xy = 1;
      xz = 0;
    } else {
      xx = 1;
      xy = 0;
      xz = 0;
    }

    ux = xy * n[2] - xz * n[1];
    uy = xz * n[0] - xx * n[2];
    uz = xx * n[1] - xy * n[0];

    r = 1 / sqrt(ux * ux + uy * uy + uz * uz);

    ux *= r;
    uy *= r;
    uz *= r;

    vx = uy * n[2] - uz * n[1];
    vy = uz * n[0] - ux * n[2];
    vz = ux * n[1] - uy * n[0];

    d[0] = ux * tx + vx * ty + n[0] * tz;
    d[1] = uy * tx + vy * ty + n[1] * tz;
    d[2] = uz * tx + vz * ty + n[2] * tz;
}

// Calculate the texture value of a ray
void getTextureRay(float color[3], float o[3], float n[3], float e[3], int block, image2d_t textures, image1d_t blockData, image2d_t grassTextures, image2d_t foliageTextures, int bounds) {
    sampler_t imageSampler = CLK_NORMALIZED_COORDS_FALSE |
                             CLK_ADDRESS_CLAMP_TO_EDGE |
                             CLK_FILTER_NEAREST;
    // Block data
    int4 blockD = read_imagei(blockData, imageSampler, block);

    // Calculate u,v value based on chunky code
    float u, v;
    float bx = floor(o[0]);
    float by = floor(o[1]);
    float bz = floor(o[2]);
    if (n[1] != 0) {
      u = o[0] - bx;
      v = o[2] - bz;
    } else if (n[0] != 0) {
      u = o[2] - bz;
      v = o[1] - by;
    } else {
      u = o[0] - bx;
      v = o[1] - by;
    }
    if (n[0] > 0 || n[2] < 0) {
      u = 1 - u;
    }
    if (n[1] > 0) {
      v = 1 - v;
    }

    u = u * 16 - EPS;
    v = (1 - v) * 16 - EPS;

    // Texture lookup index
    int index = blockD.x;
    index += 16 * (int) v + (int) u;

    // Lookup texture value
    uint4 texturePixels = read_imageui(textures, imageSampler, (int2) ((index / 4) % 8192, (index / 4) / 8192));
    unsigned int argb;

    switch (index % 4) {
        case 0:
            argb = texturePixels.x;
            break;
        case 1:
            argb = texturePixels.y;
            break;
        case 2:
            argb = texturePixels.z;
            break;
        default:
            argb = texturePixels.w;
    }

    // Separate ARGB value
    color[0] = (0xFF & (argb >> 16)) / 256.0;
    color[1] = (0xFF & (argb >> 8 )) / 256.0;
    color[2] = (0xFF & (argb >> 0 )) / 256.0;

    // Calculate tint
    if (blockD.w != 0) {
        uint4 tintLookup;
        if (blockD.w == 1) {
            tintLookup = read_imageui(grassTextures, imageSampler, (int2)((bx+bounds)/4, bz+bounds));
        } else {
            tintLookup = read_imageui(foliageTextures, imageSampler, (int2)((bx+bounds)/4, bz+bounds));
        }
        unsigned int tintColor = tintLookup.x;

        switch ((int)(bx + bounds) % 4) {
            case 0:
                tintColor = tintLookup.x;
            case 1:
                tintColor = tintLookup.y;
            case 2:
                tintColor = tintLookup.z;
            default:
                tintColor = tintLookup.w;
        }

        // Separate argb and add to color
        color[0] *= (0xFF & (tintColor >> 16)) / 256.0;
        color[1] *= (0xFF & (tintColor >> 8)) / 256.0;
        color[2] *= (0xFF & (tintColor >> 0)) / 256.0;
    }

    // Calculate emittance
    e[0] = color[0] * color[0] * (blockD.y / 256.0);
    e[1] = color[1] * color[1] * (blockD.y / 256.0);
    e[2] = color[2] * color[2] * (blockD.y / 256.0);

    // TODO: Specular reflection?
}

// Get the value of a location in the octree
int octreeGet(int x, int y, int z, int depth, image2d_t treeData) {
    int nodeIndex = 0;
    int level = depth;

    int data = octreeRead(nodeIndex, treeData);
    while (data > 0) {
        level -= 1;

        int lx = 1 & (x >> level);
        int ly = 1 & (y >> level);
        int lz = 1 & (z >> level);

        nodeIndex = data + ((lx << 2) | (ly << 1) | lz);
        data = octreeRead(nodeIndex, treeData);
    }

    return -data;
}

// Get the value in the octree array
int octreeRead(int index, image2d_t treeData) {
    sampler_t voxelSampler = CLK_NORMALIZED_COORDS_FALSE |
                             CLK_ADDRESS_CLAMP_TO_EDGE |
                             CLK_FILTER_NEAREST;

    int4 data = read_imagei(treeData, voxelSampler, (int2) ((index / 4) % 8192, (index / 4) / 8192));

    switch (index % 4) {
        case 0:
            return data.x;
        case 1:
            return data.y;
        case 2:
            return data.z;
        default:
            return data.w;
    }
}

// Check intersect with octree
// TODO: check BVH tree and custom block models
int intersect(image2d_t octreeData, int depth, int x, int y, int z, __global const int *transparent, int transparentLength) {
    int block = octreeGet(x, y, z, depth, octreeData);

    for (int i = 0; i < transparentLength; i++)
        if (block == transparent[i])
            return 0;
    return 1;
}

// Check if we are inbounds
int inbounds(float o[3], int depth) {
    int x = o[0];
    int y = o[1];
    int z = o[2];

    int lx = x >> depth;
    int ly = y >> depth;
    int lz = z >> depth;

    return lx == 0 && ly == 0 && lz == 0;
}

// Exit the current block. Based on chunky code.
void exitBlock(float o[3], float d[3], float n[3], float *distance) {
    float tNext = 10000000;

    float b[3];
    b[0] = floor(o[0]);
    b[1] = floor(o[1]);
    b[2] = floor(o[2]);

    float t = (b[0] - o[0]) / d[0];
    if (t > EPS) {
        tNext = t;
        n[0] = 1;
        n[1] = n[2] = 0;
    } else {
        t = ((b[0] + 1) - o[0]) / d[0];
        if (t < tNext && t > EPS) {
            tNext = t;
            n[0] = -1;
            n[1] = n[2] = 0;
        }
    }

    t = (b[1] - o[1]) / d[1];
    if (t < tNext && t > EPS) {
        tNext = t;
        n[1] = 1;
        n[0] = n[2] = 0;
    }
    else {
        t = ((b[1] + 1) - o[1]) / d[1];
        if (t < tNext && t > EPS) {
            tNext = t;
            n[1] = -1;
            n[0] = n[2] = 0;
        }
    }

    t = (b[2] - o[2]) / d[2];
    if (t < tNext && t > EPS) {
        tNext = t;
        n[2] = 1;
        n[0] = n[1] = 0;
    } else {
        t = ((b[2] + 1) - o[2]) / d[2];
        if (t < tNext && t > EPS) {
            tNext = t;
            n[2] = -1;
            n[0] = n[1] = 0;
        }
    }

    o[0] += OFFSET * d[0];
    o[1] += OFFSET * d[1];
    o[2] += OFFSET * d[2];

    o[0] += tNext * d[0];
    o[1] += tNext * d[1];
    o[2] += tNext * d[2];

    *distance += tNext;
}

void calcSkyRay(float direction[3], float color[3], float e[3], image2d_t skyTexture, float sunPos[3], float sunIntensity, image2d_t textures, int sunIndex) {
    sampler_t skySampler = CLK_NORMALIZED_COORDS_TRUE |
                           CLK_ADDRESS_CLAMP_TO_EDGE |
                           CLK_FILTER_LINEAR;

    // Draw sun texture
    color[0] = color[1] = color[2] = 0;
    sunIntersect(direction, color, e, sunPos, textures, sunIndex);

    float theta = atan2(direction[2], direction[0]);
    theta /= M_PI * 2;
    theta = fmod(fmod(theta, 1) + 1, 1);
    float phi = (asin(direction[1]) + M_PI_2) / M_PI;

    float4 skyColor = read_imagef(skyTexture, skySampler, (float2)(theta, phi));

    color[0] += skyColor.x;
    color[1] += skyColor.y;
    color[2] += skyColor.z;
}

void sunIntersect(float direction[3], float color[3], float e[3], float sunPos[3], image2d_t textures, int sunIndex) {
    sampler_t imageSampler = CLK_NORMALIZED_COORDS_FALSE |
                             CLK_ADDRESS_CLAMP_TO_EDGE |
                             CLK_FILTER_NEAREST;

    float su[3] = {0};
    float sv[3];
    if (fabs(sunPos[0]) > 0.1) {
        su[1] = 1;
    } else {
        su[0] = 1;
    }
    cross3(sunPos, su, sv);
    normalize3(sv);
    cross3(sv, sunPos, su);

    float radius = 0.03;
    float width = radius * 4;
    float width2 = width * 2;
    float a;
    a = M_PI / 2 - acos(dot3(direction, su)) + width;
    if (a >= 0 && a < width2) {
        float b = M_PI / 2 - acos(dot3(direction, sv)) + width;
        if (b >= 0 && b < width2) {
            int index = sunIndex;
            index += (int)((a/width2) * 32 - EPS) + (int)((b/width2) * 32 - EPS) * 32;
            uint4 texturePixels = read_imageui(textures, imageSampler, (int2) ((index / 4) % 8192, (index / 4) / 8192));
            unsigned int argb;

            switch (index % 4) {
                case 0:
                    argb = texturePixels.x;
                    break;
                case 1:
                    argb = texturePixels.y;
                    break;
                case 2:
                    argb = texturePixels.z;
                    break;
                default:
                    argb = texturePixels.w;
            }

            // Separate ARGB value
            color[0] = (0xFF & (argb >> 16)) / 256.0;
            color[1] = (0xFF & (argb >> 8 )) / 256.0;
            color[2] = (0xFF & (argb >> 0 )) / 256.0;
        }
    }
}

float dot3(float a[3], float b[3]) {
    return a[0] * b[0] + a[1] * b[1] + a[2] * b[2];
}

void cross3(float a[3], float b[3], float r[3]) {
    r[0] = a[1]*b[2] - a[2]*b[1];
    r[1] = a[2]*b[0] - a[0]*b[2];
    r[2] = a[0]*b[1] - a[1]*b[0];
}

void normalize3(float a[3]) {
    float length = sqrt(dot3(a, a));
    a[0] /= length;
    a[1] /= length;
    a[2] /= length;
}

float aabbIntersect(float o[3], float d[3], float xmin, float xmax, float ymin, float ymax, float zmin, float zmax) {
    float rx = 1 / d[0];
    float ry = 1 / d[1];
    float rz = 1 / d[2];

    float tx1 = (xmin - o[0]) * rx;
    float tx2 = (xmax - o[0]) * rx;

    float ty1 = (ymin - o[1]) * ry;
    float ty2 = (ymax - o[1]) * ry;

    float tz1 = (zmin - o[2]) * rz;
    float tz2 = (zmax - o[2]) * rz;

    float tmin = fmax(fmax(fmin(tx1, tx2), fmin(ty1, ty2)), fmin(tz1, tz2));
    float tmax = fmin(fmin(fmax(tx1, tx2), fmax(ty1, ty2)), fmax(tz1, tz2));

    return tmin <= tmax+OFFSET && tmin >= 0;
}

int aabbInside(float o[3], float xmin, float xmax, float ymin, float ymax, float zmin, float zmax) {
    return o[0] >= xmin && o[0] <= xmax &&
           o[1] >= ymin && o[1] <= ymax &&
           o[2] >= zmin && o[2] <= zmax;
}

int texturedTriangle(float color[3], float ro[3], float rd[3], float rn[3], float re[3], float* tbest, int index, image2d_t entityTrigs, image2d_t entityTextures) {
    if (indexf(entityTrigs, index) != 0) return 0;  // Not textured triangle why was this even called?

    // Check aabb
    float xmin, xmax, ymin, ymax, zmin, zmax;
    xmin = indexf(entityTrigs, index+1);
    xmax = indexf(entityTrigs, index+2);
    ymin = indexf(entityTrigs, index+3);
    ymax = indexf(entityTrigs, index+4);
    zmin = indexf(entityTrigs, index+5);
    zmax = indexf(entityTrigs, index+6);

    if (!aabbInside(ro, xmin, xmax, ymin, ymax, zmin, zmax) &&
        !aabbIntersect(ro, rd, xmin, xmax, ymin, ymax, zmin, zmax))
        return 0;   // Does not intersect

    float3 e1, e2, o, n;
    float2 t1, t2, t3;
    int doubleSided;

    e1 = (float3)(indexf(entityTrigs, index+7),
                  indexf(entityTrigs, index+8),
                  indexf(entityTrigs, index+9));

    e2 = (float3)(indexf(entityTrigs, index+10),
                  indexf(entityTrigs, index+11),
                  indexf(entityTrigs, index+12));

    o = (float3)(indexf(entityTrigs, index+13),
                 indexf(entityTrigs, index+14),
                 indexf(entityTrigs, index+15));

    n = (float3)(indexf(entityTrigs, index+16),
                 indexf(entityTrigs, index+17),
                 indexf(entityTrigs, index+18));

    t1 = (float2)(indexf(entityTrigs, index+19),
                  indexf(entityTrigs, index+20));

    t2 = (float2)(indexf(entityTrigs, index+21),
                  indexf(entityTrigs, index+22));

    t3 = (float2)(indexf(entityTrigs, index+23),
                  indexf(entityTrigs, index+24));

    doubleSided = indexf(entityTrigs, index+25);

    float3 pvec, qvec, tvec;

    pvec = cross((float3) (rd[0], rd[1], rd[2]), e2);
    float det = dot(e1, pvec);
    if (doubleSided) {
        if (det > -EPS && det < EPS) return 0;
    } else if (det > -EPS) {
        return 0;
    }
    float recip = 1 / det;

    tvec = (float3) (ro[0] - o.x, ro[1] - o.y, ro[2] - o.z);

    float u = dot(tvec, pvec) * recip;

    if (u < 0 || u > 1) return 0;

    qvec = cross(tvec, e1);

    float v = dot((float3) (rd[0], rd[1], rd[2]), qvec) * recip;

    if (v < 0 || (u+v) > 1) return 0;

    float t = dot(e2, qvec) * recip;

    if (t > EPS && t < *tbest) {
        float w = 1 - u - v;
        float2 uv = (float2) (t1.x * u + t2.x * v + t3.x * w,
                              t1.y * u + t2.y * v + t3.y * w);

        float width = indexf(entityTrigs, index+26);
        float height = indexf(entityTrigs, index+27);

        int x = uv.x * width - EPS;
        int y = (1 - uv.y) * height - EPS;

        unsigned int argb = indexu(entityTextures, width*y + x + indexf(entityTrigs, index+29));
        float emittance = indexf(entityTrigs, index+28);

        if ((0xFF & (argb >> 24)) > 0) {
            *tbest = t;

            color[0] = (0xFF & (argb >> 16)) / 256.0;
            color[1] = (0xFF & (argb >> 8 )) / 256.0;
            color[2] = (0xFF & (argb >> 0 )) / 256.0;

            re[0] = color[0] * color[0] * emittance;
            re[1] = color[1] * color[1] * emittance;
            re[2] = color[2] * color[2] * emittance;

            rn[0] = n.x;
            rn[1] = n.y;
            rn[2] = n.z;

            return 1;
        }
    }

    return 0;
}

float indexf(image2d_t img, int index) {
    float4 roi = read_imagef(img, indexSampler, (int2) ((index / 4) % 8192, (index / 4) / 8192));
    switch (index % 4) {
        case 0: return roi.x;
        case 1: return roi.y;
        case 2: return roi.z;
        default: return roi.w;
    }
}

int indexi(image2d_t img, int index) {
    int4 roi = read_imagei(img, indexSampler, (int2) ((index / 4) % 8192, (index / 4) / 8192));
    switch (index % 4) {
        case 0: return roi.x;
        case 1: return roi.y;
        case 2: return roi.z;
        default: return roi.w;
    }
}

unsigned int indexu(image2d_t img, int index) {
    uint4 roi = read_imageui(img, indexSampler, (int2) ((index / 4) % 8192, (index / 4) / 8192));
    switch (index % 4) {
        case 0: return roi.x;
        case 1: return roi.y;
        case 2: return roi.z;
        default: return roi.w;
    }
}
