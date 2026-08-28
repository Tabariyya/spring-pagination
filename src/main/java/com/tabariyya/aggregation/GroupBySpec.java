package com.tabariyya.aggregation;

import com.querydsl.core.Tuple;
import com.querydsl.core.types.Expression;
import com.querydsl.core.types.Order;
import com.querydsl.core.types.OrderSpecifier;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class GroupBySpec {

    public static final GroupBySpec EMPTY = new GroupBySpec(Collections.emptyList());

    private final List<GroupKey<?>> keys;

    public GroupBySpec(List<GroupKey<?>> keys) {
        this.keys = List.copyOf(keys);
    }

    public boolean isEmpty() {
        return keys.isEmpty();
    }

    public Expression<?>[] expressions() {
        Expression<?>[] expressions = new Expression<?>[keys.size()];
        for (int i = 0; i < keys.size(); i++) {
            expressions[i] = keys.get(i).expression();
        }
        return expressions;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public OrderSpecifier<?>[] ordering() {
        OrderSpecifier<?>[] ordering = new OrderSpecifier<?>[keys.size()];
        for (int i = 0; i < keys.size(); i++) {
            ordering[i] = new OrderSpecifier(Order.ASC, keys.get(i).expression());
        }
        return ordering;
    }

    public List<AggregationEntry> entries(Tuple tuple) {
        List<AggregationEntry> entries = new ArrayList<>(keys.size());
        for (GroupKey<?> key : keys) {
            entries.add(key.entry(tuple));
        }
        return entries;
    }

}
