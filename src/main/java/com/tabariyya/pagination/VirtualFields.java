package com.tabariyya.pagination;

import com.querydsl.core.types.Expression;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Names a client may filter and order by on an entity that are not fields of it, each standing for a
 * QueryDSL expression - typically a correlated subquery into another table:
 *
 * <pre>{@code
 * @Bean
 * VirtualFields joiningRequestFields() {
 *     return VirtualFields.of(JoiningRequest.class)
 *             .field("activity.ended", Boolean.class, JPAExpressions.select(activity.ended)
 *                     .from(activity)
 *                     .where(activity.id.eq(joiningRequest.activityId)));
 * }
 * }</pre>
 *
 * <p>Declared as beans, they are picked up by {@link QueryBuilderService}, which uses the expression
 * wherever a field's path would go: in a filter, in the ordering, and in the keyset of a cursor. The
 * name is accepted even though the response does not carry it.
 *
 * <p>The expression must be built on the root alias the library queries the entity by - the entity's
 * simple name, decapitalized, which is also what a generated Q-class uses ({@code joiningRequest}).
 *
 * <p>The expression must never be null. A cursor carries the last row's value and asks for rows past
 * it, and nothing compares past a null, so a null would silently end the listing early; a page whose
 * last row has one is refused instead. Wrap anything that can be null in {@code coalesce}.
 *
 * <p>A subquery rather than a join is what keeps a virtual field from changing which rows a query
 * returns, so it can be added to any listing. It is evaluated per row and cannot use an index for
 * ordering, which suits listings narrowed by a filter first.
 */
public final class VirtualFields {

    private final Class<?> entity;
    private final Map<String, VirtualField> fields = new LinkedHashMap<>();

    private VirtualFields(Class<?> entity) {
        this.entity = entity;
    }

    public static VirtualFields of(Class<?> entity) {
        return new VirtualFields(entity);
    }

    /**
     * Registers {@code name} as standing for {@code expression}. Cursor values and filter values are
     * converted to {@code type}. A name the entity already answers to is refused, so a registration
     * can never quietly change what an existing filter means.
     */
    public <T> VirtualFields field(String name, Class<T> type, Expression<T> expression) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("A virtual field needs a name");
        }
        if (FieldUtils.resolves(entity, name)) {
            throw new IllegalArgumentException("'" + name + "' is already a field of " + entity.getName()
                    + "; a virtual field cannot take its name");
        }
        if (fields.containsKey(name)) {
            throw new IllegalArgumentException("'" + name + "' is already registered on " + entity.getName());
        }
        fields.put(name, new VirtualField(name, type, expression));
        return this;
    }

    public Class<?> entity() {
        return entity;
    }

    public Optional<VirtualField> find(String name) {
        return Optional.ofNullable(fields.get(name));
    }

    public Map<String, VirtualField> fields() {
        return Collections.unmodifiableMap(fields);
    }

    /** One registered name: the type its values convert to, and the expression it is queried as. */
    public record VirtualField(String name, Class<?> type, Expression<?> expression) {}
}
