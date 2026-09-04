package com.tabariyya.pagination;

import jakarta.persistence.Embeddable;
import org.springframework.core.ResolvableType;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class FieldUtils {

    private FieldUtils() {
    }

    /**
     * Looks up a field on the class or any of its superclasses, so entities
     * inheriting fields from a mapped superclass are supported.
     */
    public static Field findField(Class<?> type, String fieldName) throws NoSuchFieldException {
        Field declared = declaredField(type, fieldName);
        if (declared == null) {
            throw new NoSuchFieldException(fieldName + " (searched " + type.getName() + " and its superclasses)");
        }
        return declared;
    }

    /**
     * Resolves a name a client sent into the field it means and the path that reaches it.
     *
     * <p>A dotted name is walked as written. A plain name is looked for on the entity first, and only
     * if it is not there is it looked for inside the entity's embedded values - so an entity whose key
     * is an {@link Embeddable} still answers to the names of the fields inside that key, and callers
     * that filtered by them before the key was extracted keep working.
     *
     * <p>Only {@link Embeddable} types are searched. Descending into arbitrary field types would make
     * unrelated names resolve by accident, and an embeddable is exactly the case where the field is
     * still one column on the entity's own table.
     */
    public static ResolvedField resolve(Class<?> type, String fieldName) throws NoSuchFieldException {
        if (fieldName.indexOf('.') >= 0) {
            return resolvePath(type, fieldName);
        }

        Field declared = declaredField(type, fieldName);
        if (declared != null) {
            return new ResolvedField(fieldName, declared, typeOf(declared, type));
        }

        List<ResolvedField> embedded = new ArrayList<>();
        searchEmbedded(type, fieldName, "", embedded, new HashSet<>());

        if (embedded.size() == 1) {
            return embedded.getFirst();
        }
        if (embedded.size() > 1) {
            throw new NoSuchFieldException(fieldName + " is ambiguous on " + type.getName() + ": it matches "
                    + embedded.stream().map(ResolvedField::path).toList() + ". Name the one you mean.");
        }
        throw new NoSuchFieldException(fieldName + " (searched " + type.getName()
                + ", its superclasses and its embedded values)");
    }

    private static ResolvedField resolvePath(Class<?> type, String path) throws NoSuchFieldException {
        Class<?> current = type;
        Field field = null;
        for (String segment : path.split("\\.")) {
            field = declaredField(current, segment);
            if (field == null) {
                throw new NoSuchFieldException(segment + " of " + path + " (searched " + current.getName() + ")");
            }
            current = typeOf(field, current);
        }
        return new ResolvedField(path, field, current);
    }

    private static void searchEmbedded(
            Class<?> type, String fieldName, String prefix, List<ResolvedField> found, Set<Class<?>> visited) {
        for (Class<?> current = type; current != null && current != Object.class; current = current.getSuperclass()) {
            for (Field candidate : current.getDeclaredFields()) {
                Class<?> candidateType = typeOf(candidate, type);
                if (!candidateType.isAnnotationPresent(Embeddable.class) || !visited.add(candidateType)) {
                    continue;
                }
                String nested = prefix + candidate.getName() + ".";
                Field match = declaredField(candidateType, fieldName);
                if (match != null) {
                    found.add(new ResolvedField(nested + fieldName, match, typeOf(match, candidateType)));
                }
                searchEmbedded(candidateType, fieldName, nested, found, visited);
            }
        }
    }

    /**
     * The type a field actually holds on {@code owner}. A field declared on a generic superclass
     * erases to its bound - {@code BaseModel<T extends EntityKey>.id} reflects as {@code EntityKey} -
     * so the variable is resolved against the entity that inherits it before the type is used.
     */
    private static Class<?> typeOf(Field field, Class<?> owner) {
        Class<?> resolved = ResolvableType.forField(field, owner).resolve();
        return resolved != null ? resolved : field.getType();
    }

    private static Field declaredField(Class<?> type, String fieldName) {
        for (Class<?> current = type; current != null && current != Object.class; current = current.getSuperclass()) {
            try {
                return current.getDeclaredField(fieldName);
            } catch (NoSuchFieldException ignored) {
                // keep walking up the hierarchy
            }
        }
        return null;
    }
}
