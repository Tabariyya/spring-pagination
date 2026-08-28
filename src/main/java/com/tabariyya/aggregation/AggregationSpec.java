package com.tabariyya.aggregation;

import com.querydsl.core.Tuple;
import com.querydsl.core.types.Expression;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class AggregationSpec {

    public static final AggregationSpec EMPTY = new AggregationSpec(Collections.emptyList());

    private final List<Aggregation<?>> aggregations;

    public AggregationSpec(List<Aggregation<?>> aggregations) {
        this.aggregations = Collections.unmodifiableList(new ArrayList<>(aggregations));
    }

    public boolean isEmpty() {
        return aggregations.isEmpty();
    }

    public Expression<?>[] expressions() {
        Expression<?>[] expressions = new Expression<?>[aggregations.size()];
        for (int i = 0; i < aggregations.size(); i++) {
            expressions[i] = aggregations.get(i).expression();
        }
        return expressions;
    }

    public List<AggregationEntry> entries(Tuple tuple) {
        List<AggregationEntry> entries = new ArrayList<>(aggregations.size());
        for (Aggregation<?> aggregation : aggregations) {
            entries.add(aggregation.entry(tuple));
        }
        return entries;
    }

}
