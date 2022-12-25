#ifndef CHUNKYCLPLUGIN_SCENE_H
#define CHUNKYCLPLUGIN_SCENE_H

#include "../opencl.h"
#include "block.h"

typedef struct {
    __global const int *blockPalette;
} Scene;

Block Scene_getBlock(Scene* scene, int block) {
    return Block_get(scene->blockPalette, block);
}



#endif
