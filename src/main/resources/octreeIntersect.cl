#define EPS 0.0005

int inbounds(float o[3], float bounds);
void exitBlock(float o[3], float d[3], float *distance);

__kernel void octreeIntersect(__global const float *rayPos,
                              __global const float *rayDir,
                              __global const char *voxel,
                              __global const int *bounds,
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
    d[0] = rayPos[gid*3 + 0];
    d[1] = rayPos[gid*3 + 1];
    d[2] = rayPos[gid*3 + 2];

    while (inbounds(o, *bounds)) {
        if (!voxel[(int) ((o[0] + size/2) * size * size
                        + o[1] * size
                        + (o[2] + size/2))])
            break;
        exitBlock(o, d, &distance);
    }

    res[gid] = distance;
}

int inbounds(float o[3], float bounds) {
    return o[0] < bounds && o[1] < 2*bounds && o[2] < bounds && o[0] > -bounds && o[1] > 0 && o[2] > -bounds;
}

void exitBlock(float o[3], float d[3], float *distance) {
    double tNext = 1000000;

    float b[3];
    b[0] = floor(o[0]);
    b[1] = floor(o[1]);
    b[2] = floor(o[2]);

    double t = (b[0] - o[0]) / d[0];
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