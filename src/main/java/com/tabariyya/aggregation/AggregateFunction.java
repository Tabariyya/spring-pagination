package com.tabariyya.aggregation;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public enum AggregateFunction {

    SUM("$sum"),
    AVG("$avg"),
    MIN("$min"),
    MAX("$max"),
    COUNT_DISTINCT("$countDistinct"),
    GROUPED_BY(null);

    private static final Map<String, AggregateFunction> BY_OPERATOR = byOperator();

    private final String operator;

    AggregateFunction(String operator) {
        this.operator = operator;
    }

    public String operator() {
        return operator;
    }

    static AggregateFunction of(String operator) {
        AggregateFunction function = BY_OPERATOR.get(operator);
        if (function == null) {
            throw new InvalidAggregationException("Unsupported aggregate function '" + operator
                    + "'; supported are " + String.join(", ", BY_OPERATOR.keySet()));
        }
        return function;
    }

    private static Map<String, AggregateFunction> byOperator() {
        Map<String, AggregateFunction> byOperator = new LinkedHashMap<>();
        for (AggregateFunction function : values()) {
            if (function.operator != null) {
                byOperator.put(function.operator, function);
            }
        }
        return Collections.unmodifiableMap(byOperator);
    }
}
