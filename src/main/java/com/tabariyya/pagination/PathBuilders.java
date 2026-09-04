package com.tabariyya.pagination;

import com.querydsl.core.types.dsl.PathBuilder;

public final class PathBuilders {

    private PathBuilders() {
    }

    public static <T> PathBuilder<T> of(Class<T> entity) {
        return new PathBuilder<>(entity, decapitalize(entity.getSimpleName()));
    }

    /**
     * Walks a resolved property path, so a name that landed inside an embedded value becomes the
     * nested path a query needs. {@code PathBuilder.get} takes one property at a time and reads a
     * dotted string as a single odd property name, so the segments are applied in turn.
     */
    public static PathBuilder<?> get(PathBuilder<?> root, String path) {
        PathBuilder<?> current = root;
        for (String segment : path.split("\\.")) {
            current = current.get(segment);
        }
        return current;
    }

    private static String decapitalize(String name) {
        return name.isEmpty() ? name : Character.toLowerCase(name.charAt(0)) + name.substring(1);
    }
}
