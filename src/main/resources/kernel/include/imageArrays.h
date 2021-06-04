#ifndef CHUNKYCLPLUGIN_IMAGEARRAYS_H
#define CHUNKYCLPLUGIN_IMAGEARRAYS_H

// Image sampler for data textures.
const sampler_t indexSampler = CLK_NORMALIZED_COORDS_FALSE | CLK_ADDRESS_CLAMP_TO_EDGE | CLK_FILTER_NEAREST;

// Read a single float at an index
float indexf(image2d_t img, int index) {
    float4 roi = read_imagef(img, indexSampler, (int2) ((index / 4) % 8192, (index / 4) / 8192));
    switch (index % 4) {
        case 0: return roi.x;
        case 1: return roi.y;
        case 2: return roi.z;
        default: return roi.w;
    }
}

// Read a single integer at an index
int indexi(image2d_t img, int index) {
    int4 roi = read_imagei(img, indexSampler, (int2) ((index / 4) % 8192, (index / 4) / 8192));
    switch (index % 4) {
        case 0: return roi.x;
        case 1: return roi.y;
        case 2: return roi.z;
        default: return roi.w;
    }
}

// Read a single unsigned integer at an index
unsigned int indexu(image2d_t img, int index) {
    uint4 roi = read_imageui(img, indexSampler, (int2) ((index / 4) % 8192, (index / 4) / 8192));
    switch (index % 4) {
        case 0: return roi.x;
        case 1: return roi.y;
        case 2: return roi.z;
        default: return roi.w;
    }
}

// Read an array of floats
void areadf(image2d_t img, int index, int length, float output[]) {
    float4 roi = read_imagef(img, indexSampler, (int2) ((index / 4) % 8192, (index / 4) / 8192));
    for (int i = 0; i < length; i++) {
        if ((index + i) % 4 == 0 && i != 0) {
            index += 4;
            roi = read_imagef(img, indexSampler, (int2) ((index / 4) % 8192, (index / 4) / 8192));
        }

        switch ((index + i) % 4) {
            case 0: output[i] = roi.x; break;
            case 1: output[i] = roi.y; break;
            case 2: output[i] = roi.z; break;
            default: output[i] = roi.w; break;
        }
    }
}

// Read an array of integers
void areadi(image2d_t img, int index, int length, int output[]) {
    int4 roi = read_imagei(img, indexSampler, (int2) ((index / 4) % 8192, (index / 4) / 8192));
    for (int i = 0; i < length; i++) {
        if ((index + i) % 4 == 0 && i != 0) {
            index += 4;
            roi = read_imagei(img, indexSampler, (int2) ((index / 4) % 8192, (index / 4) / 8192));
        }

        switch ((index + i) % 4) {
            case 0: output[i] = roi.x; break;
            case 1: output[i] = roi.y; break;
            case 2: output[i] = roi.z; break;
            default: output[i] = roi.w; break;
        }
    }
}

// Read an array of unsigned integers
void areadu(image2d_t img, int index, int length, unsigned int output[]) {
    uint4 roi = read_imageui(img, indexSampler, (int2) ((index / 4) % 8192, (index / 4) / 8192));
    for (int i = 0; i < length; i++) {
        if ((index + i) % 4 == 0 && i != 0) {
            index += 4;
            roi = read_imageui(img, indexSampler, (int2) ((index / 4) % 8192, (index / 4) / 8192));
        }

        switch ((index + i) % 4) {
            case 0: output[i] = roi.x; break;
            case 1: output[i] = roi.y; break;
            case 2: output[i] = roi.z; break;
            default: output[i] = roi.w; break;
        }
    }
}

#endif