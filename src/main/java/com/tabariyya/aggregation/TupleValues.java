package com.tabariyya.aggregation;

import com.tabariyya.pagination.GenericQueryDslException;
import com.querydsl.core.Tuple;
import com.querydsl.core.types.Expression;
import com.querydsl.core.util.MathUtils;

final class TupleValues {

    private TupleValues() {
    }

    static <T> T read(Tuple tuple, Expression<T> expression, Class<T> type, String name) {
        Object value = tuple.get(expression);
        if (value == null || type.isInstance(value)) {
            return type.cast(value);
        }
        if (value instanceof Number number && Number.class.isAssignableFrom(type)) {
            return type.cast(MathUtils.cast(number, type.asSubclass(Number.class)));
        }
        throw new GenericQueryDslException(new ClassCastException("'" + name + "' returned a "
                + value.getClass().getSimpleName() + ", which is not a " + type.getSimpleName()));
    }
}
