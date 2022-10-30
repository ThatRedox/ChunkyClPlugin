package dev.thatredox.chunkynative.util;

import se.llbit.log.Log;

import java.lang.reflect.Field;
import java.util.Objects;

public class Reflection {
    private Reflection() {}

    @SuppressWarnings("unchecked")
    public static <T> T getFieldValue(Object obj, String name, Class<T> cls) {
        try {
            Field field = obj.getClass().getDeclaredField(name);
            field.setAccessible(true);
            Object o = field.get(obj);
            if (o != null && cls.isAssignableFrom(o.getClass())) {
                return (T) o;
            } else {
                throw new RuntimeException(String.format(
                        "Field %s was of type %s. Expected type %s. Do you have the wrong version of Chunky?",
                        name, o == null ? null : o.getClass(), cls));
            }
        } catch (NoSuchFieldException | IllegalAccessException e) {
            Log.error("Failed to obtain field. Do you have the wrong version of Chunky?", e);
            throw new RuntimeException(e);
        }
    }

    /**
     * Copy public fields between objects.
     * @param o1 Object to copy from
     * @param o2 Object to copy to
     */
    public static <T> void copyPublic(T o1, T o2) {
        try {
            Field[] fields = o1.getClass().getFields();
            for (Field field : fields) {
                Field o2f = o2.getClass().getField(field.getName());
                o2f.set(o2, field.get(o1));
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Compare equality between two objects. Objects are compared with
     * {@link java.util.Objects#deepEquals(Object, Object)}
     */
    public static <T> boolean equalsPublic(T a, T b) {
        if (a == b) return true;
        if (!Objects.equals(a.getClass(), b.getClass())) return false;
        try {
            Field[] fields = a.getClass().getFields();
            for (Field field : fields) {
                if (!Objects.deepEquals(field.get(a), field.get(b))) {
                    return false;
                }
            }
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        }
        return true;
    }
}
