package com.tabariyya.pagination;

import java.lang.reflect.Field;

final class FieldUtils {

    private FieldUtils() {
    }

    /**
     * Looks up a field on the class or any of its superclasses, so entities
     * inheriting fields from a mapped superclass are supported.
     */
    static Field findField(Class<?> type, String fieldName) throws NoSuchFieldException {
        for (Class<?> current = type; current != null && current != Object.class; current = current.getSuperclass()) {
            try {
                return current.getDeclaredField(fieldName);
            } catch (NoSuchFieldException ignored) {
                // keep walking up the hierarchy
            }
        }
        throw new NoSuchFieldException(fieldName + " (searched " + type.getName() + " and its superclasses)");
    }
}
