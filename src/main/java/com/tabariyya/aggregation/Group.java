package com.tabariyya.aggregation;

import java.util.Map;

public record Group(Map<String, Object> key, Map<String, Object> values) {
}
