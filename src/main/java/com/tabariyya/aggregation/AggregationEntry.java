package com.tabariyya.aggregation;

public record AggregationEntry(String key, Object value, AggregateFunction operation) {}
