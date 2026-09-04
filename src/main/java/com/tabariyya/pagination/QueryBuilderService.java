package com.tabariyya.pagination;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.querydsl.core.BooleanBuilder;
import com.querydsl.core.types.*;
import com.querydsl.core.types.dsl.Expressions;
import com.querydsl.core.types.dsl.PathBuilder;
import org.springframework.stereotype.Service;

import java.lang.reflect.Field;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.temporal.ChronoField;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class QueryBuilderService {

    private static final ObjectMapper objectMapper = new ObjectMapper();
    private final JacksonValueConverter converter = new JacksonValueConverter(objectMapper);

    private static final DateTimeFormatter dateTimeFormatter = new DateTimeFormatterBuilder()
            .appendPattern("yyyy-MM-dd'T'HH:mm:ss")
            .appendFraction(ChronoField.NANO_OF_SECOND, 0, 9, true)
            .toFormatter();

    public Predicate buildFilter(Class<?> entity, String query) {
        try {
            return predicateBuilder(entity, query);
        } catch (Throwable e) {
            throw new GenericQueryDslException(e);
        }
    }

    public OrderSpecifier<?>[] buildOrderSpecifier(Class<?> entity, String query) {
        try {
            return orderSpecifierBuilder(entity, query);
        } catch (Throwable e) {
            throw new GenericQueryDslException(e);
        }
    }

    /**
     * Rejects any filter or sort field that the response type does not declare,
     * so clients can only filter and order by fields they can see in the
     * response body. Must run on the client-provided sort, before
     * {@link #ensureIdTieBreaker} appends the internal id tie-breaker.
     */
    public void validateFieldsAgainstResponse(Class<?> responseType, String filterQuery, String orderingQuery) {
        try {
            if (filterQuery != null && !filterQuery.isEmpty()) {
                checkFilterFields(decodeAndDeserialize(filterQuery), responseType);
            }
            if (orderingQuery != null && !orderingQuery.isEmpty()) {
                JsonNode root = decodeAndDeserialize(orderingQuery);
                if (root.isObject()) {
                    root.fieldNames().forEachRemaining(fieldName -> checkResponseField(responseType, fieldName));
                }
            }
        } catch (UnknownResponseFieldException e) {
            throw e;
        } catch (Throwable e) {
            throw new GenericQueryDslException(e);
        }
    }

    private void checkFilterFields(JsonNode node, Class<?> responseType) {
        if (!node.isObject()) {
            return;
        }
        node.fields().forEachRemaining(entry -> {
            if (entry.getKey().startsWith("$")) {
                JsonNode value = entry.getValue();
                if (value.isArray()) {
                    value.forEach(item -> checkFilterFields(item, responseType));
                } else {
                    checkFilterFields(value, responseType);
                }
            } else {
                checkResponseField(responseType, entry.getKey());
            }
        });
    }

    private void checkResponseField(Class<?> responseType, String fieldName) {
        try {
            FieldUtils.resolve(responseType, fieldName);
        } catch (NoSuchFieldException e) {
            throw new UnknownResponseFieldException(
                    "Cannot filter or order by '" + fieldName + "': the response does not contain this field");
        }
    }

    /**
     * Appends {"id": 1} to the sort specification when "id" is not already present,
     * so that the ordering is total and keyset pagination never skips or repeats rows.
     */
    /**
     * The ordering as the caller wrote it, url-decoded and re-serialized so that it reads the same
     * way {@link #ensureIdTieBreaker} leaves the applied one - without adding the tie-breaker, since
     * this is meant to record what was asked for rather than what will run.
     */
    public String normalizeOrdering(String query) {
        try {
            return objectMapper.writeValueAsString(decodeAndDeserialize(query));
        } catch (Throwable e) {
            throw new GenericQueryDslException(e);
        }
    }

    public String ensureIdTieBreaker(String query) {
        try {
            JsonNode root = decodeAndDeserialize(query);
            if (!root.isObject()) {
                throw new IllegalArgumentException("Sort specification must be a JSON object");
            }
            ObjectNode sortNode = (ObjectNode) root;
            if (!sortNode.has("id")) {
                sortNode.put("id", 1);
            }
            return objectMapper.writeValueAsString(sortNode);
        } catch (Throwable e) {
            throw new GenericQueryDslException(e);
        }
    }

    /**
     * Builds the keyset predicate that selects rows strictly after the last seen row:
     * (f1 &gt; v1) OR (f1 = v1 AND f2 &gt; v2) OR ... with &gt;/&lt; chosen per field direction.
     */
    public Predicate buildKeysetPredicate(Class<?> entity, String orderingQuery, Map<String, Object> lastValues) {
        try {
            PathBuilder<?> pathBuilder = PathBuilders.of(entity);
            JsonNode root = decodeAndDeserialize(orderingQuery);

            List<String> fieldNames = new ArrayList<>();
            List<Order> orders = new ArrayList<>();
            List<Object> values = new ArrayList<>();

            Iterator<Map.Entry<String, JsonNode>> fields = root.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> entry = fields.next();
                String fieldName = entry.getKey();
                if (!lastValues.containsKey(fieldName)) {
                    throw new InvalidCursorException("Cursor is missing last value for sort field: " + fieldName);
                }
                ResolvedField resolved = FieldUtils.resolve(entity, fieldName);
                Class<?> fieldType = resolved.type();
                fieldNames.add(resolved.path());
                orders.add(entry.getValue().asInt() == 1 ? Order.ASC : Order.DESC);
                values.add(convertValue(lastValues.get(fieldName), fieldType));
            }

            BooleanBuilder keyset = new BooleanBuilder();
            for (int i = 0; i < fieldNames.size(); i++) {
                BooleanBuilder branch = new BooleanBuilder();
                for (int j = 0; j < i; j++) {
                    branch.and(Expressions.predicate(
                            Ops.EQ,
                            PathBuilders.get(pathBuilder, fieldNames.get(j)),
                            ConstantImpl.create(values.get(j))
                    ));
                }
                Ops comparison = (orders.get(i) == Order.ASC) ? Ops.GT : Ops.LT;
                branch.and(Expressions.predicate(
                        comparison,
                        PathBuilders.get(pathBuilder, fieldNames.get(i)),
                        ConstantImpl.create(values.get(i))
                ));
                keyset.or(branch);
            }

            return keyset;
        } catch (InvalidCursorException e) {
            throw e;
        } catch (Throwable e) {
            throw new GenericQueryDslException(e);
        }
    }

    private OrderSpecifier<?>[] orderSpecifierBuilder(Class<?> entity, String query) throws Throwable {
        PathBuilder<?> pathBuilder = PathBuilders.of(entity);
        JsonNode root = decodeAndDeserialize(query);

        if (!root.isObject()) {
            throw new IllegalArgumentException("Sort specification must be a JSON object");
        }

        List<OrderSpecifier<?>> orderSpecifiers = new ArrayList<>();
        Iterator<Map.Entry<String, JsonNode>> fields = root.fields();

        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> entry = fields.next();
            String fieldName = entry.getKey();
            JsonNode valueNode = entry.getValue();

            if (!valueNode.isInt()) {
                throw new IllegalArgumentException("Sort value for field '" + fieldName + "' must be 1 (ASC) or -1 (DESC)");
            }

            int sortValue = valueNode.asInt();
            if (sortValue != 1 && sortValue != -1) {
                throw new IllegalArgumentException("Invalid sort value for field '" + fieldName + "': must be 1 or -1");
            }

            Order order = (sortValue == 1) ? Order.ASC : Order.DESC;
            Expression<?> fieldPath = PathBuilders.get(pathBuilder, FieldUtils.resolve(entity, fieldName).path());
            orderSpecifiers.add(new OrderSpecifier(order, fieldPath));
        }

        return orderSpecifiers.toArray(new OrderSpecifier[0]);
    }

    /**
     * Converts a MongoDB query language string to a Predicate.
     *
     * @param entity the name of the entity to query (e.g., "User")
     * @param query  the MongoDB query language string containing a $match clause
     * @return a {@link Predicate} representing the query conditions
     * @throws NoClassDefFoundError     if there is a capitalization typo in the class name (e.g., "User" vs "user")
     * @throws ClassNotFoundException   if the specified entity class cannot be found
     * @throws NoSuchFieldException     if the specified field does not exist in the entity class
     * @throws IllegalArgumentException if a type conversion fails (e.g., converting a String to a LocalDateTime);
     *                                  this can be resolved with a custom converter in the {@link #convertValue} method
     * @throws IllegalArgumentException if the operator is not compatible with either the field type or the value type
     * @throws ClassCastException       if the operator is not compatible with either the field type or the value type
     */
    private Predicate predicateBuilder(Class<?> entity, String query) throws Throwable {
        PathBuilder<?> pathBuilder = PathBuilders.of(entity);
        JsonNode root = decodeAndDeserialize(query);
        return processNode(root, pathBuilder, entity);
    }

    private Object convertValue(Object rawValue, Class<?> targetType) {

        return converter.convertValue(rawValue,targetType);
    }

    private Predicate processNode(JsonNode node, PathBuilder<?> pathBuilder, Class<?> entityClass) throws Exception {
        if (!node.isObject()) {
            throw new IllegalArgumentException("Expected an object node");
        }

        BooleanBuilder builder = new BooleanBuilder();

        Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> entry = fields.next();
            String key = entry.getKey();
            JsonNode value = entry.getValue();

            Predicate predicate;

            if (key.startsWith("$")) {
                if ("$and".equals(key)) {
                    predicate = processAndOperator(value, pathBuilder, entityClass);
                } else if ("$or".equals(key)) {
                    predicate = processOrOperator(value, pathBuilder, entityClass);
                } else if ("$nor".equals(key)) {
                    predicate = processNorOperator(value, pathBuilder, entityClass);
                } else if ("$not".equals(key)) {
                    predicate = processNotOperator(value, pathBuilder, entityClass);
                } else {
                    throw new IllegalArgumentException("Unsupported operator: " + key);
                }
            } else {
                predicate = processFieldCondition(key, value, pathBuilder, entityClass);
            }

            builder.and(predicate);
        }

        return builder;
    }

    private Predicate processAndOperator(JsonNode array, PathBuilder<?> pathBuilder, Class<?> entityClass) throws Exception {
        if (!array.isArray()) {
            throw new IllegalArgumentException("$and requires an array");
        }

        BooleanBuilder builder = new BooleanBuilder();

        for (JsonNode item : array) {
            Predicate predicate = processNode(item, pathBuilder, entityClass);
            builder.and(predicate);
        }

        return builder;
    }

    private Predicate processOrOperator(JsonNode array, PathBuilder<?> pathBuilder, Class<?> entityClass) throws Exception {
        if (!array.isArray()) {
            throw new IllegalArgumentException("$or requires an array");
        }

        BooleanBuilder builder = new BooleanBuilder();

        for (JsonNode item : array) {
            Predicate predicate = processNode(item, pathBuilder, entityClass);
            builder.or(predicate);
        }

        return builder;
    }

    private Predicate processNorOperator(JsonNode array, PathBuilder<?> pathBuilder, Class<?> entityClass) throws Exception {
        return processOrOperator(array, pathBuilder, entityClass).not();
    }

    private Predicate processNotOperator(JsonNode array, PathBuilder<?> pathBuilder, Class<?> entityClass) throws Exception {
        return processNode(array, pathBuilder, entityClass).not();
    }

    private Predicate processFieldCondition(String fieldName, JsonNode condition, PathBuilder<?> pathBuilder, Class<?> entityClass) throws Exception {
        ResolvedField resolved = FieldUtils.resolve(entityClass, fieldName);
        Field field = resolved.field();
        PathBuilder<?> fieldPath = PathBuilders.get(pathBuilder, resolved.path());

        if (!condition.isObject()) {
            Object convertedValue = convertValue(condition.asText(), field.getType());
            return Expressions.predicate(
                    Ops.EQ,
                    fieldPath,
                    ConstantImpl.create(convertedValue)
            );
        }

        Iterator<Map.Entry<String, JsonNode>> operators = condition.fields();
        if (!operators.hasNext()) {
            throw new IllegalArgumentException("Empty condition for field: " + fieldName);
        }

        Map.Entry<String, JsonNode> entry = operators.next();
        String operatorKey = entry.getKey();
        JsonNode valueNode = entry.getValue();

        if ("$not".equals(operatorKey)) {
            return processFieldCondition(fieldName, valueNode, pathBuilder, entityClass).not();
        }

        if ("$regex".equals(operatorKey)) {
            String regex = valueNode.asText();

            return Expressions.booleanTemplate(
                    "function('regex_matches_ic', {0}, {1}) = true",
                    fieldPath,
                    ConstantImpl.create(regex)
            );
        }

        if ("$similar".equals(operatorKey)) {
            String term = valueNode.asText();

            return Expressions.booleanTemplate(
                    "function('trgm_similar', {0}, {1}) = true",
                    fieldPath,
                    ConstantImpl.create(term)
            );
        }

        Ops queryDslOp = mapMongoOperator(operatorKey);

        if (queryDslOp == Ops.IN || queryDslOp == Ops.NOT_IN) {
            if (!valueNode.isArray()) {
                throw new IllegalArgumentException(operatorKey + " requires an array");
            }
            List<Object> values = new ArrayList<>();
            for (JsonNode element : valueNode) {
                Object convertedElement = convertValue(parseMongoValue(element), field.getType());
                values.add(convertedElement);
            }
            return Expressions.predicate(
                    queryDslOp,
                    fieldPath,
                    ConstantImpl.create(values)
            );
        } else {
            Object convertedValue = convertValue(parseMongoValue(valueNode), field.getType());
            return Expressions.predicate(
                    queryDslOp,
                    fieldPath,
                    ConstantImpl.create(convertedValue)
            );
        }
    }

    private Ops mapMongoOperator(String mongoOp) {
        switch (mongoOp) {
            case "$eq": return Ops.EQ;
            case "$ne": return Ops.NE;
            case "$gt": return Ops.GT;
            case "$gte": return Ops.GOE;
            case "$lt": return Ops.LT;
            case "$lte": return Ops.LOE;
            case "$in": return Ops.IN;
            case "$nin": return Ops.NOT_IN;
            case "$contains": return Ops.STRING_CONTAINS_IC;
            default: throw new IllegalArgumentException("Unsupported operator: " + mongoOp);
        }
    }

    private Object parseMongoValue(JsonNode valueNode) {
        if (!valueNode.isTextual()) {
            return valueNode.asText();
        }
        String text = valueNode.asText();
        Pattern datePattern = Pattern.compile("new\\s+Date\\(\"([^\"]+)\"\\)");
        Matcher matcher = datePattern.matcher(text);
        return matcher.find() ? matcher.group(1) : text;
    }

    private JsonNode decodeAndDeserialize(String query) throws Exception {
        String urlDecodedQuery = URLDecoder.decode(query, StandardCharsets.UTF_8.name());
        return objectMapper.readTree(urlDecodedQuery);
    }
}
