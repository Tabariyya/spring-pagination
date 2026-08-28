package com.tabariyya.aggregation;

import com.querydsl.core.Tuple;
import com.querydsl.core.types.Expression;

import java.util.Objects;

public record GroupKey<T>(String field, Class<T> type, Expression<T> expression) {

    public GroupKey {
        Objects.requireNonNull(field, "field");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(expression, "expression");
    }

    public T extract(Tuple tuple) {
        return TupleValues.read(tuple, expression, type, field);
    }

    public AggregationEntry entry(Tuple tuple) {
        return new AggregationEntry(field, extract(tuple), AggregationEntry.GROUPED_BY);
    }
}
