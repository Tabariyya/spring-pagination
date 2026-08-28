package com.tabariyya.aggregation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.querydsl.core.types.dsl.PathBuilder;
import com.tabariyya.pagination.PathBuilders;
import com.tabariyya.pagination.FieldUtils;
import com.tabariyya.pagination.GenericQueryDslException;
import com.tabariyya.pagination.UnknownResponseFieldException;
import org.springframework.stereotype.Service;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.function.Consumer;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class AggregationQueryBuilder {

    public static final String GROUP_KEY = "$groupBy";

    private static final ObjectMapper objectMapper = new ObjectMapper();

    public AggregationSpec buildAggregations(Class<?> entity, String query) {
        try {
            return aggregationBuilder(entity, query);
        } catch (InvalidAggregationException e) {
            throw e;
        } catch (Throwable e) {
            throw new GenericQueryDslException(e);
        }
    }

    public GroupBySpec buildGroupBy(Class<?> entity, String query) {
        try {
            return groupByBuilder(entity, query);
        } catch (InvalidAggregationException e) {
            throw e;
        } catch (Throwable e) {
            throw new GenericQueryDslException(e);
        }
    }

    public void validateAggregationsAgainstResponse(Class<?> responseType, String aggregationQuery) {
        validateAggregations(aggregationQuery, fieldName -> checkResponseField(responseType, fieldName));
    }

    public void validateAggregationsAgainstFields(String[] allowedFields, String aggregationQuery) {
        Set<String> allowed = new LinkedHashSet<>(Arrays.asList(allowedFields));
        validateAggregations(aggregationQuery, fieldName -> checkAllowedField(allowed, fieldName, "aggregate"));
    }

    public void validateGroupByAgainstResponse(Class<?> responseType, String groupByQuery) {
        validateGroupBy(groupByQuery, fieldName -> checkResponseField(responseType, fieldName));
    }

    public void validateGroupByAgainstFields(String[] allowedFields, String groupByQuery) {
        Set<String> allowed = new LinkedHashSet<>(Arrays.asList(allowedFields));
        validateGroupBy(groupByQuery, fieldName -> checkAllowedField(allowed, fieldName, "group by"));
    }

    private void validateAggregations(String aggregationQuery, Consumer<String> check) {
        if (aggregationQuery == null || aggregationQuery.isEmpty()) {
            return;
        }
        try {
            JsonNode root = decodeAndDeserialize(aggregationQuery);
            if (!root.isObject()) {
                return;
            }
            root.fields().forEachRemaining(entry -> {
                if (GROUP_KEY.equals(entry.getKey())) {
                    return;
                }
                String fieldName = aggregatedFieldName(entry.getValue());
                if (fieldName != null) {
                    check.accept(fieldName);
                }
            });
        } catch (UnknownResponseFieldException e) {
            throw e;
        } catch (Throwable e) {
            throw new GenericQueryDslException(e);
        }
    }

    private void validateGroupBy(String groupByQuery, Consumer<String> check) {
        if (groupByQuery == null || groupByQuery.isEmpty()) {
            return;
        }
        try {
            groupByFieldNames(groupByQuery).forEach(check);
        } catch (UnknownResponseFieldException | InvalidAggregationException e) {
            throw e;
        } catch (Throwable e) {
            throw new GenericQueryDslException(e);
        }
    }

    public void validateFiltersAgainstFields(String[] allowedFields, String filterQuery) {
        if (filterQuery == null || filterQuery.isEmpty()) {
            return;
        }
        try {
            Set<String> allowed = new LinkedHashSet<>(Arrays.asList(allowedFields));
            collectFilterFields(decodeAndDeserialize(filterQuery)).forEach(f -> checkAllowedField(allowed, f, "filter by"));
        } catch (UnknownResponseFieldException e) {
            throw e;
        } catch (Throwable e) {
            throw new GenericQueryDslException(e);
        }
    }

    private List<String> collectFilterFields(JsonNode node) {
        List<String> names = new ArrayList<>();
        if (!node.isObject()) {
            return names;
        }
        node.fields().forEachRemaining(entry -> {
            if (entry.getKey().startsWith("$")) {
                JsonNode value = entry.getValue();
                if (value.isArray()) {
                    value.forEach(item -> names.addAll(collectFilterFields(item)));
                } else {
                    names.addAll(collectFilterFields(value));
                }
            } else {
                names.add(entry.getKey());
            }
        });
        return names;
    }

    private void checkAllowedField(Set<String> allowed, String fieldName, String action) {
        if (!allowed.contains(fieldName)) {
            throw new UnknownResponseFieldException("Cannot " + action + " '" + fieldName
                    + "' on this endpoint; fields you can " + action + " are "
                    + (allowed.isEmpty() ? "none" : String.join(", ", allowed)));
        }
    }

    private String aggregatedFieldName(JsonNode operand) {
        return operand.isTextual() ? stripFieldPrefix(operand.asText()) : null;
    }

    private AggregationSpec aggregationBuilder(Class<?> entity, String query) throws Throwable {
        PathBuilder<?> pathBuilder = PathBuilders.of(entity);
        JsonNode root = decodeAndDeserialize(query);

        if (!root.isObject()) {
            throw new InvalidAggregationException("Aggregation specification must be a JSON object");
        }

        List<Aggregation<?>> aggregations = new ArrayList<>();
        Iterator<Map.Entry<String, JsonNode>> fields = root.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> entry = fields.next();
            if (GROUP_KEY.equals(entry.getKey())) {
                continue;
            }
            aggregations.add(buildAggregation(entry.getKey(), entry.getValue(), pathBuilder, entity));
        }

        return aggregations.isEmpty() ? AggregationSpec.EMPTY : new AggregationSpec(aggregations);
    }

    private GroupBySpec groupByBuilder(Class<?> entity, String query) throws Throwable {
        PathBuilder<?> pathBuilder = PathBuilders.of(entity);
        List<String> fieldNames = groupByFieldNames(query);

        if (fieldNames.isEmpty()) {
            throw new InvalidAggregationException(
                    GROUP_KEY + " is required; name at least one field to group the aggregates by");
        }

        List<GroupKey<?>> keys = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (String fieldName : fieldNames) {
            if (!seen.add(fieldName)) {
                throw new InvalidAggregationException(
                        GROUP_KEY + " names '" + fieldName + "' more than once");
            }
            keys.add(AggregateExpressions.key(entity, pathBuilder, fieldName));
        }

        return new GroupBySpec(keys);
    }

    private List<String> groupByFieldNames(String query) throws Exception {
        JsonNode root = decodeAndDeserialize(query);
        if (!root.isObject()) {
            throw new InvalidAggregationException("Aggregation specification must be a JSON object");
        }

        JsonNode key = root.get(GROUP_KEY);
        if (key == null || key.isNull()) {
            return List.of();
        }

        List<String> fieldNames = new ArrayList<>();
        if (key.isTextual()) {
            for (String part : key.asText().split(",", -1)) {
                fieldNames.add(stripFieldPrefix(part.trim()));
            }
        } else if (key.isArray()) {
            for (JsonNode element : key) {
                if (!element.isTextual()) {
                    throw new InvalidAggregationException(
                            GROUP_KEY + " must hold field names as strings, for example [\"$score\"]");
                }
                fieldNames.add(stripFieldPrefix(element.asText().trim()));
            }
        } else {
            throw new InvalidAggregationException(
                    GROUP_KEY + " must be a field name or an array of field names");
        }

        for (String fieldName : fieldNames) {
            if (fieldName.isEmpty()) {
                throw new InvalidAggregationException(GROUP_KEY + " contains an empty field name");
            }
        }
        return fieldNames;
    }

    private Aggregation<?> buildAggregation(String operator, JsonNode operand,
                                            PathBuilder<?> pathBuilder, Class<?> entityClass) throws NoSuchFieldException {
        AggregateFunction function = AggregateFunction.of(operator);

        if (function == AggregateFunction.COUNT) {
            if (!operand.isNull() && !(operand.isObject() && operand.isEmpty())) {
                throw new InvalidAggregationException("$count counts rows and takes no field;"
                        + " write {\"$count\": {}}, or $countDistinct to count the distinct values of a field");
            }
            return AggregateExpressions.count();
        }
        if (function == AggregateFunction.SUM && operand.isNumber()) {
            if (!operand.isIntegralNumber() || operand.asInt() != 1) {
                throw new InvalidAggregationException("{\"$sum\": 1} is the only literal sum supported;"
                        + " sum a field with {\"$sum\": \"$field\"}");
            }
            return AggregateExpressions.count();
        }

        String fieldName = fieldReference(function, operand);
        return AggregateExpressions.build(entityClass, pathBuilder, function, fieldName);
    }

    private static String fieldReference(AggregateFunction function, JsonNode operand) {
        if (!operand.isTextual()) {
            throw new InvalidAggregationException(function.operator()
                    + " needs a field reference such as \"$fieldName\"");
        }
        String fieldName = stripFieldPrefix(operand.asText());
        if (fieldName.isEmpty()) {
            throw new InvalidAggregationException(function.operator()
                    + " needs a non-empty field reference");
        }
        return fieldName;
    }

    private static String stripFieldPrefix(String reference) {
        return reference.startsWith("$") ? reference.substring(1) : reference;
    }

    private void checkResponseField(Class<?> responseType, String fieldName) {
        try {
            FieldUtils.findField(responseType, fieldName);
        } catch (NoSuchFieldException e) {
            throw new UnknownResponseFieldException(
                    "Cannot aggregate or group by '" + fieldName + "': the response does not contain this field");
        }
    }

    private JsonNode decodeAndDeserialize(String query) throws Exception {
        return objectMapper.readTree(URLDecoder.decode(query, StandardCharsets.UTF_8.name()));
    }
}
