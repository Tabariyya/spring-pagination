package com.tabariyya.pagination;

import java.lang.reflect.Field;

/**
 * A field name from a request, resolved against the entity: the reflective field it landed on, the
 * property path that reaches it, and the type it actually holds on that entity.
 *
 * <p>The path differs from the name once a name resolves into an embedded value. A filter naming
 * {@code followeeUserId} on an entity whose key is embedded resolves to the field declared on that
 * key, reached by the path {@code id.followeeUserId} - which is what a query has to be built from.
 *
 * <p>The type is carried separately because a field declared on a generic superclass erases to its
 * bound: {@code BaseModel<T extends EntityKey>.id} reflects as {@code EntityKey}, while on a given
 * entity it is that entity's own key class.
 */
public record ResolvedField(String path, Field field, Class<?> type) {

    /** Reads this field out of an entity instance, walking the path a segment at a time. */
    public Object valueIn(Object root) throws NoSuchFieldException, IllegalAccessException {
        Object current = root;
        for (String segment : path.split("\\.")) {
            if (current == null) {
                return null;
            }
            Field declared = FieldUtils.findField(current.getClass(), segment);
            declared.setAccessible(true);
            current = declared.get(current);
        }
        return current;
    }
}
