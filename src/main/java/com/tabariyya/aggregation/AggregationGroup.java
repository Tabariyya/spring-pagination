package com.tabariyya.aggregation;

import java.util.List;

public record AggregationGroup(List<AggregationEntry> data, double percentage, long total) {
}
