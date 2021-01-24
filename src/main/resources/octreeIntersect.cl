#define EPS 0.0005

void getTexturePixel(float color[3], int block, int x, int y, image3d_t textures);
int intersect(image2d_t octreeData, int depth, int x, int y, int z, __global const int transparent[], int transparentLength);
int octreeGet(int x, int y, int z, int bounds, image2d_t treeData);
int octreeRead(int index, image2d_t treeData);
int inbounds(float o[3], int bounds);
void exitBlock(float o[3], float d[3], int n[3], float *distance);

__kernel void octreeIntersect(__global const float *rayPos,
                              __global const float *rayDir,
                              __global const int *depth,
                              image2d_t octreeData,
                              __global const int *voxelLength,
                              __global const int *transparent,
                              __global const int *transparentLength,
                              image3d_t textures,
                              __global float *res)
{
    int gid = get_global_id(0);
    float distance = 0;

    float o[3];
    o[0] = rayPos[0];
    o[1] = rayPos[1];
    o[2] = rayPos[2];

    float d[3];
    d[0] = rayDir[gid*3 + 0];
    d[1] = rayDir[gid*3 + 1];
    d[2] = rayDir[gid*3 + 2];

    int n[3];

    int i;
    int hit = 0;
    for (i = 0; i < 256; i++) {
        if (!intersect(octreeData, *depth, o[0], o[1], o[2], transparent, *transparentLength))
            exitBlock(o, d, n, &distance);
        else
        {
            hit = 1;
            break;
        }
    }

    float color[3];
    if (hit) {
        float texcoords[2];
        if (n[0] == 1 || n[0] == -1) {
            texcoords[0] = o[1];
            texcoords[1] = o[2];
        } else if (n[1] == 1 || n[1] == -1) {
            texcoords[0] = o[0];
            texcoords[1] = o[2];
        } else if (n[2] == 1 || n[2] == -1) {
            texcoords[0] = o[0];
            texcoords[1] = o[1];
        }

        getTexturePixel(color, octreeGet(o[0], o[1], o[2], *depth, octreeData),
                        (int) (texcoords[0] * 16 + 0.5) % 16,
                        (int) (texcoords[1] * 16 + 0.5) % 16, textures);
    } else {
        color[0] = 1.0;
        color[1] = 1.0;
        color[2] = 1.0;
    }

    res[gid*3 + 0] = color[0];
    res[gid*3 + 1] = color[1];
    res[gid*3 + 2] = color[2];
}

void getTexturePixel(float color[3], int block, int x, int y, image3d_t textures) {
    sampler_t imageSampler = CLK_NORMALIZED_COORDS_FALSE |
                             CLK_ADDRESS_CLAMP_TO_EDGE |
                             CLK_FILTER_NEAREST;

    uint4 argb = read_imageui(textures, imageSampler, (int4) (x, y, block, 0));

    color[0] = (0xFF & (argb.x >> 16)) / 256.0;
    color[1] = (0xFF & (argb.x >> 8 )) / 256.0;
    color[2] = (0xFF & (argb.x >> 0 )) / 256.0;
}

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

int octreeRead(int index, image2d_t treeData) {
    sampler_t voxelSampler = CLK_NORMALIZED_COORDS_FALSE |
                             CLK_ADDRESS_CLAMP_TO_EDGE |
                             CLK_FILTER_NEAREST;

    int4 data = read_imagei(treeData, voxelSampler, (int2) (index % 8192, index / 8192));
    return data.x;
}

int intersect(image2d_t octreeData, int depth, int x, int y, int z, __global const int transparent[], int transparentLength) {
    int block = octreeGet(x, y, z, depth, octreeData);

    for (int i = 0; i < transparentLength; i++)
        if (block == transparent[i])
            return 0;
    return 1;
}

int inbounds(float o[3], int depth) {
    float bounds = pow((float) 2, (float) (depth-1));
    return o[0] < bounds && o[1] < 2*bounds && o[2] < bounds && o[0] > -bounds && o[1] > 0 && o[2] > -bounds;
}

void exitBlock(float o[3], float d[3], int n[3], float *distance) {
    float tNext = 1000000000;

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

    tNext += EPS;

    o[0] += tNext * d[0];
    o[1] += tNext * d[1];
    o[2] += tNext * d[2];

    *distance += tNext;
}
