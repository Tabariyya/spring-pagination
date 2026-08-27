package com.tabariyya.aggregation;

import com.querydsl.core.Tuple;
import com.querydsl.core.types.Expression;
import com.querydsl.core.types.Order;
import com.querydsl.core.types.OrderSpecifier;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class GroupBySpec {

    public static final GroupBySpec EMPTY = new GroupBySpec(Collections.emptyList());

    private final List<GroupKey<?>> keys;

    public GroupBySpec(List<GroupKey<?>> keys) {
        this.keys = Collections.unmodifiableList(new ArrayList<>(keys));
    }

    public boolean isEmpty() {
        return keys.isEmpty();
    }

    public List<GroupKey<?>> keys() {
        return keys;
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

    public Map<String, Object> read(Tuple tuple) {
        Map<String, Object> values = new LinkedHashMap<>();
        for (GroupKey<?> key : keys) {
            values.put(key.field(), key.extract(tuple));
        }
        return Collections.unmodifiableMap(values);
    }
}
