package com.tabariyya.pagination;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.querydsl.core.BooleanBuilder;
import com.querydsl.core.types.*;
import com.querydsl.core.types.dsl.Expressions;
import com.querydsl.core.types.dsl.PathBuilder;
import org.springframework.stereotype.Service;

import java.lang.reflect.Field;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
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

    private OrderSpecifier<?>[] orderSpecifierBuilder(Class<?> entity, String query) throws Throwable {
        PathBuilder<?> pathBuilder = pathBuilderOf(entity);
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
            Expression<?> fieldPath = pathBuilder.get(fieldName);
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
        PathBuilder<?> pathBuilder = pathBuilderOf(entity);
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
        Field field = entityClass.getDeclaredField(fieldName);

        if (!condition.isObject()) {
            Object convertedValue = convertValue(condition.asText(), field.getType());
            return Expressions.predicate(
                    Ops.EQ,
                    pathBuilder.get(fieldName),
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
                    "function('regexp_match', {0}, {1}) is not null",
                    pathBuilder.get(fieldName),
                    ConstantImpl.create(regex)
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
                    pathBuilder.get(fieldName),
                    ConstantImpl.create(values)
            );
        } else {
            Object convertedValue = convertValue(parseMongoValue(valueNode), field.getType());
            return Expressions.predicate(
                    queryDslOp,
                    pathBuilder.get(fieldName),
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

    private Class<?> qClassOf(Class<?> entity) throws ClassNotFoundException {
        String qClassName = entity.getPackage().getName() + ".Q" + entity.getSimpleName();
        return Class.forName(qClassName);
    }

    private String instanceNameOf(Class<?> entity) {
        String entityName = entity.getSimpleName();
        return entityName.substring(0, 1).toLowerCase() + entityName.substring(1);
    }

    private PathBuilder<?> pathBuilderOf(Class<?> entity) throws NoSuchFieldException, ClassNotFoundException, IllegalAccessException {
        String entityInstanceName = instanceNameOf(entity);
        Class<?> qClass = qClassOf(entity);
        EntityPath<?> entityPath = (EntityPath<?>) qClass.getField(entityInstanceName).get(null);
        return new PathBuilder<>(entityPath.getType(), entityPath.getMetadata());
    }

    private JsonNode decodeAndDeserialize(String query) throws Exception {
        String urlDecodedQuery = URLDecoder.decode(query, StandardCharsets.UTF_8.name());
        return objectMapper.readTree(urlDecodedQuery);
    }
}
