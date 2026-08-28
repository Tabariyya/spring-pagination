package com.tabariyya.pagination;

import com.querydsl.core.types.dsl.PathBuilder;

public final class PathBuilders {

    private PathBuilders() {
    }

    public static <T> PathBuilder<T> of(Class<T> entity) {
        return new PathBuilder<>(entity, decapitalize(entity.getSimpleName()));
    }

    private static String decapitalize(String name) {
        return name.isEmpty() ? name : Character.toLowerCase(name.charAt(0)) + name.substring(1);
    }
}
