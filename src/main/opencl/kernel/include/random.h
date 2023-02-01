#ifndef CHUNKYCLPLUGIN_RANDOMNESS_H
#define CHUNKYCLPLUGIN_RANDOMNESS_H

typedef unsigned int* Random;

// PCG hash from
// https://www.reedbeta.com/blog/hash-functions-for-gpu-rendering/
unsigned int Random_nextState(Random random) {
    *random = *random * 47796405u + 2891336453u;
    *random = ((*random >> ((*random >> 28u) + 4u)) ^ *random) * 277803737u;
    *random = (*random >> 22u) ^ *random;
    return *random;
}

// Calculate the next float based on the formula on
// https://docs.oracle.com/javase/8/docs/api/java/util/Random.html#nextFloat--
float Random_nextFloat(Random random) {
    return (Random_nextState(random) >> 8) / ((float) (1 << 24));
}

#endif
