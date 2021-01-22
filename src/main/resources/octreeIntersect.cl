#define EPS 0.0005

int intersect(image3d_t voxel, int size, int x, int y, int z, int transparent[], int transparentLength);
int inbounds(float o[3], float bounds);
void exitBlock(float o[3], float d[3], float *distance);

__kernel void octreeIntersect(__global const float *rayPos,
                              __global const float *rayDir,
                              __global const int *bounds,
                              image3d_t voxel,
                              __global const int *voxelLength,
                              __global const int *transparent,
                              __global const int *transparentLength,
                              __global float *res)
{
    int gid = get_global_id(0);
    int size = *bounds * 2;
    float distance = 0;

    float o[3];
    o[0] = rayPos[gid*3 + 0];
    o[1] = rayPos[gid*3 + 1];
    o[2] = rayPos[gid*3 + 2];

    float d[3];
    d[0] = rayDir[gid*3 + 0];
    d[1] = rayDir[gid*3 + 1];
    d[2] = rayDir[gid*3 + 2];

    for (int i = 0; i < 64; i++) {
        if (!intersect(voxel, size, o[0], o[1], o[2], transparent, *transparentLength))
            exitBlock(o, d, &distance);
        else
            break;
    }

    res[gid] = distance;
}

int octreeGet(int x, int y, int z, int bounds, image3d_t treeData) {
    sampler_t voxelSampler = CLK_NORMALIZED_COORDS_FALSE |
                             CLK_ADDRESS_CLAMP_TO_EDGE |
                             CLK_FILTER_NEAREST;

    int nodeIndex = 0;
    int level = bounds*2;

    int data = read_imagei(treeData, voxelSampler, (int4) (nodeIndex, 0, 0, 0)).x;
    while (data > 0) {
        level -= 1;

        int lx = 1 & (x >> level);
        int ly = 1 & (y >> level);
        int lz = 1 & (z >> level);

        nodeIndex = data + ((lx << 2) | (ly << 1) | lz);
        data = read_imagei(treeData, voxelSampler, (int4) (nodeIndex, 0, 0, 0)).x;
    }

    return -data;
}

int intersect(image3d_t voxel, int size, int x, int y, int z, int transparent[], int transparentLength) {
    int block = octreeGet(x, y, z, size/2, voxel);

    for (int i = 0; i < transparentLength; i++)
        if (block == transparent[i])
            return 1;
    return 0;
}

int inbounds(float o[3], float bounds) {
    return o[0] < bounds && o[1] < 2*bounds && o[2] < bounds && o[0] > -bounds && o[1] > 0 && o[2] > -bounds;
}

void exitBlock(float o[3], float d[3], float *distance) {
    float tNext = 1000000000;

    float b[3];
    b[0] = floor(o[0]);
    b[1] = floor(o[1]);
    b[2] = floor(o[2]);

    float t = (b[0] - o[0]) / d[0];
    if (t > EPS) {
        tNext = t;
    } else {
        t = ((b[0] + 1) - o[0]) / d[0];
        if (t < tNext && t > EPS) {
            tNext = t;
        }
    }

    t = (b[1] - o[1]) / d[1];
    if (t < tNext && t > EPS) {
        tNext = t;
    }
    else {
        t = ((b[1] + 1) - o[1]) / d[1];
        if (t < tNext && t > EPS) {
            tNext = t;
        }
    }

    t = (b[2] - o[2]) / d[2];
    if (t < tNext && t > EPS) {
        tNext = t;
    } else {
        t = ((b[2] + 1) - o[2]) / d[2];
        if (t < tNext && t > EPS) {
            tNext = t;
        }
    }

    tNext += EPS;

    o[0] += tNext * d[0];
    o[1] += tNext * d[1];
    o[2] += tNext * d[2];

    *distance += tNext;
}