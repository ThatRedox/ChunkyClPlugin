package dev.thatredox.chunkynative.common.state;

import dev.thatredox.chunkynative.util.EqualityChecker;
import se.llbit.math.Vector3;
import se.llbit.math.Vector4;

import java.util.Iterator;
import java.util.List;

public class StateUtil {
    private StateUtil() {}

    public static boolean equals(Vector3 a, Vector3 b) {
        if (a == b) return true;
        if (a == null) return false;
        if (a.x != b.x) return false;
        if (a.y != b.y) return false;
        return a.z == b.z;
    }

    public static boolean equals(Vector4 a, Vector4 b) {
        if (a == b) return true;
        if (a == null) return false;
        if (a.x != b.x) return false;
        if (a.y != b.y) return false;
        if (a.z != b.z) return false;
        return a.w == b.w;
    }

    public static <T> boolean equals(T[] a, T[] b, EqualityChecker<T> checker) {
        if (a.length != b.length) return false;
        for (int i = 0; i < a.length; i++)
            if (!checker.equals(a[i], b[i])) return false;
        return true;
    }

    public static <T> boolean equals(List<T> a, List<T> b, EqualityChecker<T> checker) {
        if (a.size() != b.size()) return false;
        Iterator<T> iterA = a.iterator();
        Iterator<T> iterB = b.iterator();
        while (iterA.hasNext() && iterB.hasNext())
            if (!checker.equals(iterA.next(), iterB.next())) return false;
        return true;
    }
}
