package dev.thatredox.chunkynative.util;

import se.llbit.math.Matrix3;
import se.llbit.math.Vector3;

public class Util {
    public static float[] vector3ToFloat(Vector3 vec) {
        return new float[] { (float) vec.x, (float) vec.y, (float) vec.z };
    }

    public static float[] matrix3ToFloat(Matrix3 mat) {
        return new float[] {
            (float) mat.m11, (float) mat.m12, (float) mat.m13,
            (float) mat.m21, (float) mat.m22, (float) mat.m23,
            (float) mat.m31, (float) mat.m32, (float) mat.m33,
        };
    }
}
