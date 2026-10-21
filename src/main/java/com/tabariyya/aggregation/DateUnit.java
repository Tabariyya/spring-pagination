package com.tabariyya.aggregation;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public enum DateUnit {

    DAY("day"),
    WEEK("week"),
    MONTH("month"),
    YEAR("year");

    private static final Map<String, DateUnit> BY_NAME = byName();

    private final String unitName;

    DateUnit(String unitName) {
        this.unitName = unitName;
    }

    public String unitName() {
        return unitName;
    }

    static DateUnit of(String name) {
        DateUnit unit = BY_NAME.get(name);
        if (unit == null) {
            throw new InvalidAggregationException("Unsupported date unit '" + name
                    + "'; supported are " + String.join(", ", BY_NAME.keySet()));
        }
        return unit;
    }

    private static Map<String, DateUnit> byName() {
        Map<String, DateUnit> byName = new LinkedHashMap<>();
        for (DateUnit unit : values()) {
            byName.put(unit.unitName, unit);
        }
        return Collections.unmodifiableMap(byName);
    }
}
