package com.tabariyya.pagination;

import com.querydsl.core.Tuple;
import com.querydsl.core.types.Expression;

import java.util.Objects;

public record Aggregation<T>(String alias, Class<T> resultType, Expression<T> expression) {

    public Aggregation {
        Objects.requireNonNull(alias, "alias");
        Objects.requireNonNull(resultType, "resultType");
        Objects.requireNonNull(expression, "expression");
    }

    public T extract(Tuple tuple) {
        return TupleValues.read(tuple, expression, resultType, alias);
    }
}
