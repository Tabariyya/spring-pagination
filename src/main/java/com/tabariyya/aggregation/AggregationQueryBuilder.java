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
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class AggregationQueryBuilder {

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
        if (aggregationQuery == null || aggregationQuery.isEmpty()) {
            return;
        }
        try {
            JsonNode root = decodeAndDeserialize(aggregationQuery);
            if (!root.isObject()) {
                return;
            }
            root.fields().forEachRemaining(entry -> {
                String fieldName = aggregatedFieldName(entry.getValue());
                if (fieldName != null) {
                    checkResponseField(responseType, fieldName);
                }
            });
        } catch (UnknownResponseFieldException e) {
            throw e;
        } catch (Throwable e) {
            throw new GenericQueryDslException(e);
        }
    }

    public void validateGroupByAgainstResponse(Class<?> responseType, String groupByQuery) {
        if (groupByQuery == null || groupByQuery.isEmpty()) {
            return;
        }
        try {
            for (String fieldName : groupByFieldNames(groupByQuery)) {
                checkResponseField(responseType, fieldName);
            }
        } catch (UnknownResponseFieldException e) {
            throw e;
        } catch (InvalidAggregationException e) {
            throw e;
        } catch (Throwable e) {
            throw new GenericQueryDslException(e);
        }
    }

    public void validateAggregationsAgainstFields(String[] allowedFields, String aggregationQuery) {
        if (aggregationQuery == null || aggregationQuery.isEmpty()) {
            return;
        }
        try {
            JsonNode root = decodeAndDeserialize(aggregationQuery);
            if (!root.isObject()) {
                return;
            }
            Set<String> allowed = new LinkedHashSet<>(Arrays.asList(allowedFields));
            root.fields().forEachRemaining(entry -> {
                String fieldName = aggregatedFieldName(entry.getValue());
                if (fieldName != null) {
                    checkAllowedField(allowed, fieldName, "aggregate");
                }
            });
        } catch (UnknownResponseFieldException e) {
            throw e;
        } catch (Throwable e) {
            throw new GenericQueryDslException(e);
        }
    }

    public void validateGroupByAgainstFields(String[] allowedFields, String groupByQuery) {
        if (groupByQuery == null || groupByQuery.isEmpty()) {
            return;
        }
        try {
            Set<String> allowed = new LinkedHashSet<>(Arrays.asList(allowedFields));
            for (String fieldName : groupByFieldNames(groupByQuery)) {
                checkAllowedField(allowed, fieldName, "group by");
            }
        } catch (UnknownResponseFieldException e) {
            throw e;
        } catch (InvalidAggregationException e) {
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

    private String aggregatedFieldName(JsonNode accumulator) {
        if (!accumulator.isObject() || accumulator.size() != 1) {
            return null;
        }
        JsonNode operand = accumulator.fields().next().getValue();
        return operand.isTextual() ? stripFieldPrefix(operand.asText()) : null;
    }

    private AggregationSpec aggregationBuilder(Class<?> entity, String query) throws Throwable {
        PathBuilder<?> pathBuilder = pathBuilderOf(entity);
        JsonNode root = decodeAndDeserialize(query);

        if (!root.isObject()) {
            throw new InvalidAggregationException("Aggregation specification must be a JSON object");
        }

        List<Aggregation<?>> aggregations = new ArrayList<>();
        Iterator<Map.Entry<String, JsonNode>> fields = root.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> entry = fields.next();
            aggregations.add(buildAggregation(entry.getKey(), entry.getValue(), pathBuilder, entity));
        }

        return aggregations.isEmpty() ? AggregationSpec.EMPTY : new AggregationSpec(aggregations);
    }

    private GroupBySpec groupByBuilder(Class<?> entity, String query) throws Throwable {
        PathBuilder<?> pathBuilder = pathBuilderOf(entity);
        List<String> fieldNames = groupByFieldNames(query);

        if (fieldNames.isEmpty()) {
            throw new InvalidAggregationException("Group by must name at least one field");
        }

        List<GroupKey<?>> keys = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (String fieldName : fieldNames) {
            if (!seen.add(fieldName)) {
                throw new InvalidAggregationException("Group by names '" + fieldName + "' more than once");
            }
            keys.add(AggregateExpressions.key(entity, pathBuilder, fieldName));
        }

        return new GroupBySpec(keys);
    }

    private List<String> groupByFieldNames(String query) throws Exception {
        String decoded = URLDecoder.decode(query, StandardCharsets.UTF_8.name()).trim();
        List<String> fieldNames = new ArrayList<>();

        if (decoded.startsWith("[") || decoded.startsWith("\"")) {
            JsonNode root = objectMapper.readTree(decoded);
            if (root.isTextual()) {
                fieldNames.add(stripFieldPrefix(root.asText().trim()));
            } else if (root.isArray()) {
                for (JsonNode element : root) {
                    if (!element.isTextual()) {
                        throw new InvalidAggregationException(
                                "Group by array must hold field names as strings, for example [\"score\"]");
                    }
                    fieldNames.add(stripFieldPrefix(element.asText().trim()));
                }
            } else {
                throw new InvalidAggregationException(
                        "Group by must be a field name or an array of field names");
            }
        } else {
            for (String part : decoded.split(",", -1)) {
                fieldNames.add(stripFieldPrefix(part.trim()));
            }
        }

        for (String fieldName : fieldNames) {
            if (fieldName.isEmpty()) {
                throw new InvalidAggregationException("Group by contains an empty field name");
            }
        }
        return fieldNames;
    }

    private Aggregation<?> buildAggregation(String alias, JsonNode accumulator,
                                            PathBuilder<?> pathBuilder, Class<?> entityClass) throws NoSuchFieldException {
        if (alias.isEmpty() || alias.startsWith("$")) {
            throw new InvalidAggregationException("Invalid aggregation alias '" + alias
                    + "': aliases are the names the results are reported under and must not start with '$'");
        }
        if (!accumulator.isObject() || accumulator.size() != 1) {
            throw new InvalidAggregationException("Aggregation '" + alias
                    + "' must be an object holding exactly one accumulator, for example {\"$sum\": \"$score\"}");
        }

        Map.Entry<String, JsonNode> entry = accumulator.fields().next();
        AggregateFunction function = AggregateFunction.of(entry.getKey());
        JsonNode operand = entry.getValue();

        if (function == AggregateFunction.COUNT) {
            if (!operand.isNull() && !(operand.isObject() && operand.isEmpty())) {
                throw new InvalidAggregationException("Aggregation '" + alias
                        + "': $count counts rows and takes no field; write {\"$count\": {}},"
                        + " or $countDistinct to count the distinct values of a field");
            }
            return AggregateExpressions.count(alias);
        }
        if (function == AggregateFunction.SUM && operand.isNumber()) {
            if (!operand.isIntegralNumber() || operand.asInt() != 1) {
                throw new InvalidAggregationException("Aggregation '" + alias
                        + "': {\"$sum\": 1} is the only literal sum supported; sum a field with {\"$sum\": \"$field\"}");
            }
            return AggregateExpressions.count(alias);
        }

        String fieldName = fieldReference(alias, function, operand);
        return AggregateExpressions.build(entityClass, pathBuilder, function, alias, fieldName);
    }

    private static String fieldReference(String alias, AggregateFunction function, JsonNode operand) {
        if (!operand.isTextual()) {
            throw new InvalidAggregationException("Aggregation '" + alias + "': " + function.operator()
                    + " needs a field reference such as \"$fieldName\"");
        }
        String fieldName = stripFieldPrefix(operand.asText());
        if (fieldName.isEmpty()) {
            throw new InvalidAggregationException("Aggregation '" + alias + "': " + function.operator()
                    + " needs a non-empty field reference");
        }
        return fieldName;
    }

    private static String stripFieldPrefix(String reference) {
        return reference.startsWith("$") ? reference.substring(1) : reference;
    }

    private PathBuilder<?> pathBuilderOf(Class<?> entity) {
        return PathBuilders.of(entity);
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
