#ifndef KERNEL_DOUBLE_H
#define KERNEL_DOUBLE_H

#include "../opencl.h"

#if defined(cl_khr_fp64)
#pragma OPENCL EXTENSION cl_khr_fp64: enable
#define CL_DOUBLE_SUPPORT
#elif defined(cl_amd_fp64)
#pragma OPENCL EXTENSION cl_amd_fp64: enable
#define CL_DOUBLE_SUPPORT
#endif

typedef ulong imposter_double;

/// Convert an (imposter) double to a float.
float idouble_to_float(imposter_double value) {
#ifdef CL_DOUBLE_SUPPORT
    return (float) as_double(value);
#else
    float sign = ((value >> 63) == 0) ? 1 : -1;
    uint exponent = (value >> 52) & 0x7FF;
    ulong mantissa = value & ((1ull << 52) - 1);

    if (exponent == 0) {
        // Subnormal
        return sign * 0.0f;
    } else if (exponent == 0x7FF) {
        // NAN or INFINITY
        if (mantissa == 0) {
            return sign * INFINITY;
        } else {
            return NAN;
        }
    } else {
        return sign * ldexp(1 + ((float) mantissa / (1ull << 52)), exponent - 1023);
    }
#endif
}

#endif
