#ifndef CHUNKYCLPLUGIN_RANDOMNESS_H
#define CHUNKYCLPLUGIN_RANDOMNESS_H

// Xorshift random number generator based on the `xorshift32` presented in
// https://en.wikipedia.org/w/index.php?title=Xorshift&oldid=1007951001
void xorshift(unsigned int *state) {
    *state ^= *state << 13;
    *state ^= *state >> 17;
    *state ^= *state << 5;
    *state *= 0x5DEECE66D;
}

// Calculate the next float based on the formula on
// https://docs.oracle.com/javase/8/docs/api/java/util/Random.html#nextFloat--
float nextFloat(unsigned int *state) {
    xorshift(state);

    return (*state >> 8) / ((float) (1 << 24));
}

#endif //CHUNKYCLPLUGIN_RANDOMNESS_H
