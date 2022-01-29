package dev.thatredox.chunkynative.opencl.util;

import se.llbit.log.Log;

import java.lang.reflect.Field;

public class Reflection {
    private Reflection() {}

    public Object getFieldValue(Object obj, String name) {
        try {
            Field field = obj.getClass().getDeclaredField(name);
            field.setAccessible(true);
            return field.get(obj);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            Log.error("Failed to obtain field. Do you have the wrong version of Chunky?", e);
            throw new RuntimeException(e);
        }
    }
}
