package com.tabariyya.pagination;

import java.util.Map;

public class Cursor {
    private String filters;
    private String ordering;
    private Integer size;
    private Map<String, Object> lastValues;
    private Map<String, Object> attributes;

    public Cursor() {
    }

    public Cursor(String filters, String ordering, Integer size, Map<String, Object> lastValues,
                  Map<String, Object> attributes) {
        this.filters = filters;
        this.ordering = ordering;
        this.size = size;
        this.lastValues = lastValues;
        this.attributes = attributes;
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

    /**
     * Free-form values the application put on the {@link QuerySpec} while serving
     * the previous page; carried along so they are available again on the next one.
     */
    public Map<String, Object> getAttributes() {
        return attributes;
    }
    public void setAttributes(Map<String, Object> attributes) {
        this.attributes = attributes;
    }
}
