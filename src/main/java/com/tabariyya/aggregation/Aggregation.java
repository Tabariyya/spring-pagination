package com.tabariyya.aggregation;

import com.querydsl.core.Tuple;
import com.querydsl.core.types.Expression;

import java.util.Objects;

public record Aggregation<T>(
        String field, AggregateFunction function, Class<T> resultType, Expression<T> expression) {

    public Aggregation {
        Objects.requireNonNull(function, "function");
        Objects.requireNonNull(resultType, "resultType");
        Objects.requireNonNull(expression, "expression");
    }

    public T extract(Tuple tuple) {
        return TupleValues.read(tuple, expression, resultType, function.operator());
    }

    public AggregationEntry entry(Tuple tuple) {
        return new AggregationEntry(field, extract(tuple), function);
    }
}
