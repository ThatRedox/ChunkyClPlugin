#ifndef CHUNKYCLPLUGIN_RANDOMNESS_H
#define CHUNKYCLPLUGIN_RANDOMNESS_H

// PCG hash from
// https://www.reedbeta.com/blog/hash-functions-for-gpu-rendering/
unsigned int Random_nextState(unsigned int *state) {
    *state = *state * 47796405u + 2891336453u;
    *state = ((*state >> ((*state >> 28u) + 4u)) ^ *state) * 277803737u;
    *state = (*state >> 22u) ^ *state;
    return *state;
}

// Calculate the next float based on the formula on
// https://docs.oracle.com/javase/8/docs/api/java/util/Random.html#nextFloat--
float Random_nextFloat(unsigned int *state) {
    return (nextState(state) >> 8) / ((float) (1 << 24));
}

#endif
