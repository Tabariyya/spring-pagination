package com.tabariyya.aggregation;

import com.querydsl.core.Tuple;
import com.querydsl.core.types.Expression;

import java.util.Objects;

public record Aggregation<T>(
        String alias, String field, AggregateFunction function, Class<T> resultType, Expression<T> expression) {

    public Aggregation {
        Objects.requireNonNull(alias, "alias");
        Objects.requireNonNull(function, "function");
        Objects.requireNonNull(resultType, "resultType");
        Objects.requireNonNull(expression, "expression");
    }

    public T extract(Tuple tuple) {
        return TupleValues.read(tuple, expression, resultType, alias);
    }

    public AggregationEntry entry(Tuple tuple) {
        return new AggregationEntry(field == null ? alias : field, extract(tuple), function.name());
    }
}
