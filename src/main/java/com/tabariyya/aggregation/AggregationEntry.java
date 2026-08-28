package com.tabariyya.aggregation;

public record AggregationEntry(String key, Object value, String operation) {

    public static final String GROUPED_BY = "GROUPED_BY";
}
