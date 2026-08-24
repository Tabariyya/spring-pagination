package com.tabariyya.pagination;

import com.querydsl.core.Tuple;
import com.querydsl.core.types.Expression;
import com.querydsl.core.util.MathUtils;

import java.util.Objects;

public record Aggregation<T>(String alias, Class<T> resultType, Expression<T> expression) {

    public Aggregation {
        Objects.requireNonNull(alias, "alias");
        Objects.requireNonNull(resultType, "resultType");
        Objects.requireNonNull(expression, "expression");
    }

    public T extract(Tuple tuple) {
        Object value = tuple.get(expression);
        if (value == null || resultType.isInstance(value)) {
            return resultType.cast(value);
        }
        if (value instanceof Number number && Number.class.isAssignableFrom(resultType)) {
            return resultType.cast(MathUtils.cast(number, resultType.asSubclass(Number.class)));
        }
        throw new GenericQueryDslException(new ClassCastException("Aggregate '" + alias + "' returned a "
                + value.getClass().getSimpleName() + ", which is not a " + resultType.getSimpleName()));
    }
}
