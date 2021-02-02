#define EPS 0.000005    // Ray epsilon and exit offset
#define OFFSET 0.0001   // TODO: refine these values?

void getTextureRay(float color[3], float o[3], float n[3], float e[3], int block, image2d_t textures, image1d_t blockData);
int intersect(image2d_t octreeData, int depth, int x, int y, int z, __global const int *transparent, int transparentLength);
int octreeGet(int x, int y, int z, int bounds, image2d_t treeData);
int octreeRead(int index, image2d_t treeData);
int inbounds(float o[3], int bounds);
void exitBlock(float o[3], float d[3], float n[3], float *distance);
void diffuseReflect(float d[3], float o[3], float n[3], unsigned int *state);

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
                        __global float *res)
{
    int gid = get_global_id(0);
    float distance = 0;

    // Initialize rng
    unsigned int rngState = *seed * (gid+1);
    unsigned int *random = &rngState;
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

    float jitter = sqrt(junk[0]*junk[0] + junk[1]*junk[1] + junk[2]*junk[2]);

    d[0] += nextFloat(random) * jitter;
    d[1] += nextFloat(random) * jitter;
    d[2] += nextFloat(random) * jitter;

    // Cap max bounces at 23 since no dynamic memory allocation
    int maxbounces = *rayDepth;
    if (maxbounces > 23) maxbounces = 23;

    // Ray bounce data stacks
    float colorStack[3 * 24] = {0};
    float emittanceStack[3 * 24] = {0};

    // Do the bounces
    for (int bounces = 0; bounces < maxbounces; bounces++)
    {
        float e[3] = {0};
        int hit = 0;

        // Ray march 256 times
        // TODO: Maybe march until octree exit? Test performance impact.
        for (int i = 0; i < 256; i++) {
            if (!intersect(octreeData, *depth, o[0], o[1], o[2], transparent, *transparentLength))
                exitBlock(o, d, n, &distance);
            else
            {
                hit = 1;
                break;
            }

            if (!inbounds(o, *depth))
                break;
        }

        // Set color to sky color (1, 1, 1) or texture color
        // TODO: Implement Nishita sky
        float color[3];
        if (hit) {
            getTextureRay(color, o, n, e, octreeGet(o[0], o[1], o[2], *depth, octreeData), textures, blockData);
        } else {
            color[0] = 1;
            color[1] = 1;
            color[2] = 1;

            emittanceStack[bounces*3 + 0] = color[0] * color[0];
            emittanceStack[bounces*3 + 1] = color[1] * color[1];
            emittanceStack[bounces*3 + 2] = color[2] * color[2];

            emittanceStack[bounces*3 + 3] = color[0] * color[0];
            emittanceStack[bounces*3 + 4] = color[1] * color[1];
            emittanceStack[bounces*3 + 5] = color[2] * color[2];

            // Set color stack to sky color
            for (int i = bounces; i < maxbounces; i++) {
                colorStack[bounces*3 + 0] = color[0];
                colorStack[bounces*3 + 1] = color[1];
                colorStack[bounces*3 + 2] = color[2];
            }

            // Exit on sky hit
            break;
        }

        // Add color and emittance to proper stacks
        colorStack[bounces*3 + 0] = color[0];
        colorStack[bounces*3 + 1] = color[1];
        colorStack[bounces*3 + 2] = color[2];

        emittanceStack[bounces*3 + 0] = e[0];
        emittanceStack[bounces*3 + 1] = e[1];
        emittanceStack[bounces*3 + 2] = e[2];

        // Exit on sky-hit
        if (!hit) break;

        // Calculate new diffuse reflection ray
        // TODO: Implement specular reflection
        diffuseReflect(d, o, n, random);
        exitBlock(o, d, junk, &distance);
    }

    if (*preview) {
        // preview shading = first intersect color * sun&ambient shading
        double shading = n[0] * 0.25 + n[1]*0.866 + n[2]*0.433;
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
void getTextureRay(float color[3], float o[3], float n[3], float e[3], int block, image2d_t textures, image1d_t blockData) {
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
