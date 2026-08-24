package com.tabariyya.pagination;

import java.util.Map;

public class Cursor {
    private String filters;
    private String ordering;
    private String aggregations;
    private Integer size;
    private Map<String, Object> lastValues;

    public Cursor() {
    }

    public Cursor(String filters, String ordering, String aggregations, Integer size, Map<String, Object> lastValues) {
        this.filters = filters;
        this.ordering = ordering;
        this.aggregations = aggregations;
        this.size = size;
        this.lastValues = lastValues;
    }

    public String getFilters() {
        return filters;
    }
    public void setFilters(String filters) {
        this.filters = filters;
    }

    public String getOrdering() {
        return ordering;
    }
    public void setOrdering(String ordering) {
        this.ordering = ordering;
    }

    public String getAggregations() {
        return aggregations;
    }
    public void setAggregations(String aggregations) {
        this.aggregations = aggregations;
    }

    public Integer getSize() {
        return size;
    }
    public void setSize(Integer size) {
        this.size = size;
    }

    public Map<String, Object> getLastValues() {
        return lastValues;
    }
    public void setLastValues(Map<String, Object> lastValues) {
        this.lastValues = lastValues;
    }
}
