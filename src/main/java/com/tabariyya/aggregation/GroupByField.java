package com.tabariyya.aggregation;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.Map;

public record GroupByField(String name, DateUnit dateUnit, boolean isAge) {

    static final String DATE_TRUNC = "$dateTrunc";
    static final String AGE = "$age";

    static GroupByField plain(String reference) {
        return new GroupByField(fieldName(reference, AggregationQueryBuilder.GROUP_KEY), null, false);
    }

    static GroupByField from(JsonNode element) {
        if (element.isTextual()) {
            return plain(element.asText());
        }
        if (!element.isObject() || element.size() != 1) {
            throw new InvalidAggregationException(AggregationQueryBuilder.GROUP_KEY
                    + " holds field names such as \"$score\" or one operator such as"
                    + " {\"" + DATE_TRUNC + "\": {\"date\": \"$createdAt\", \"unit\": \"day\"}}");
        }

        Map.Entry<String, JsonNode> operator = element.fields().next();
        if (DATE_TRUNC.equals(operator.getKey())) {
            return dateTrunc(operator.getValue());
        }
        if (AGE.equals(operator.getKey())) {
            return age(operator.getValue());
        }
        throw new InvalidAggregationException("Unsupported group by operator '" + operator.getKey()
                + "'; supported are " + DATE_TRUNC + ", " + AGE);
    }

    boolean truncatesDate() {
        return dateUnit != null;
    }

    boolean isTransformed() {
        return dateUnit != null || isAge;
    }

    String operatorName() {
        return truncatesDate() ? DATE_TRUNC : AGE;
    }

    private static GroupByField dateTrunc(JsonNode operand) {
        JsonNode date = operand.get("date");
        JsonNode unit = operand.get("unit");
        if (!operand.isObject() || date == null || !date.isTextual() || unit == null || !unit.isTextual()) {
            throw new InvalidAggregationException(DATE_TRUNC
                    + " needs a date field and a unit, for example {\"date\": \"$createdAt\", \"unit\": \"day\"}");
        }
        return new GroupByField(fieldName(date.asText(), DATE_TRUNC), DateUnit.of(unit.asText()), false);
    }

    private static GroupByField age(JsonNode operand) {
        if (!operand.isTextual()) {
            throw new InvalidAggregationException(AGE + " needs a date field reference such as \"$dateOfBirth\"");
        }
        return new GroupByField(fieldName(operand.asText(), AGE), null, true);
    }

    private static String fieldName(String reference, String owner) {
        String trimmed = reference.trim();
        String name = trimmed.startsWith("$") ? trimmed.substring(1) : trimmed;
        if (name.isEmpty()) {
            throw new InvalidAggregationException(owner + " contains an empty field name");
        }
        return name;
    }
}
