package com.tabariyya.aggregation;

import com.tabariyya.pagination.FieldUtils;
import com.querydsl.core.types.Expression;
import com.querydsl.core.types.Operator;
import com.querydsl.core.types.Ops;
import com.querydsl.core.types.dsl.ComparablePath;
import com.querydsl.core.types.dsl.Expressions;
import com.querydsl.core.types.dsl.NumberPath;
import com.querydsl.core.types.dsl.PathBuilder;

import java.lang.reflect.Field;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Map;

final class AggregateExpressions {

    private static final Map<Class<?>, Class<?>> BOXED_PRIMITIVES = Map.of(
            byte.class, Byte.class,
            short.class, Short.class,
            int.class, Integer.class,
            long.class, Long.class,
            float.class, Float.class,
            double.class, Double.class,
            char.class, Character.class,
            boolean.class, Boolean.class);

    private AggregateExpressions() {
    }

    static Aggregation<?> build(Class<?> entity, PathBuilder<?> pathBuilder,
                                AggregateFunction function, String fieldName) throws NoSuchFieldException {
        Class<?> fieldType = fieldType(entity, fieldName);
        switch (function) {
            case SUM:
                return sum(fieldName, pathBuilder, requireNumeric(function, fieldName, fieldType));
            case AVG:
                return numberAggregation(fieldName, function, Ops.AggOps.AVG_AGG, Double.class,
                        numberPath(pathBuilder, fieldName, requireNumeric(function, fieldName, fieldType)));
            case MIN:
            case MAX:
                return extremum(fieldName, function,
                        requireComparable(function, fieldName, fieldType), pathBuilder);
            case COUNT_DISTINCT:
                return countDistinct(fieldName, pathBuilder);
            default:
                throw new InvalidAggregationException("Unsupported aggregate function '" + function.operator() + "'");
        }
    }

    static GroupKey<?> key(Class<?> entity, PathBuilder<?> pathBuilder, String fieldName) throws NoSuchFieldException {
        return groupKey(pathBuilder, fieldName, fieldType(entity, fieldName));
    }

    static Class<?> fieldType(Class<?> entity, String fieldName) throws NoSuchFieldException {
        Field field = FieldUtils.findField(entity, fieldName);
        return box(field.getType());
    }

    static Class<?> requireNumeric(AggregateFunction function, String fieldName, Class<?> fieldType) {
        if (!Number.class.isAssignableFrom(fieldType) || !Comparable.class.isAssignableFrom(fieldType)) {
            throw new InvalidAggregationException(function.operator()
                    + " needs a numeric field, but '" + fieldName + "' is " + fieldType.getSimpleName());
        }
        return fieldType;
    }

    static Class<?> requireComparable(AggregateFunction function, String fieldName, Class<?> fieldType) {
        if (!Comparable.class.isAssignableFrom(fieldType)) {
            throw new InvalidAggregationException(function.operator()
                    + " needs a comparable field, but '" + fieldName + "' is " + fieldType.getSimpleName());
        }
        return fieldType;
    }

    static Class<?> box(Class<?> type) {
        return type.isPrimitive() ? BOXED_PRIMITIVES.get(type) : type;
    }

    static <T> GroupKey<T> groupKey(PathBuilder<?> pathBuilder, String fieldName, Class<T> fieldType) {
        return new GroupKey<>(fieldName, fieldType, pathBuilder.getSimple(fieldName, fieldType));
    }

    private static Aggregation<Long> countDistinct(String field, PathBuilder<?> pathBuilder) {
        return new Aggregation<>(field, AggregateFunction.COUNT_DISTINCT, Long.class,
                Expressions.numberOperation(Long.class, Ops.AggOps.COUNT_DISTINCT_AGG, pathBuilder.get(field)));
    }

    private static Aggregation<?> sum(
            String field, PathBuilder<?> pathBuilder, Class<?> boxedFieldType) {
        NumberPath<?> path = numberPath(pathBuilder, field, boxedFieldType);
        if (boxedFieldType == BigDecimal.class) {
            return numberAggregation(field, AggregateFunction.SUM, Ops.AggOps.SUM_AGG, BigDecimal.class, path);
        }
        if (boxedFieldType == BigInteger.class) {
            return numberAggregation(field, AggregateFunction.SUM, Ops.AggOps.SUM_AGG, BigInteger.class, path);
        }
        if (boxedFieldType == Double.class || boxedFieldType == Float.class) {
            return numberAggregation(field, AggregateFunction.SUM, Ops.AggOps.SUM_AGG, Double.class, path);
        }
        return numberAggregation(field, AggregateFunction.SUM, Ops.AggOps.SUM_AGG, Long.class, path);
    }

    private static <R extends Number & Comparable<R>> Aggregation<R> numberAggregation(
            String field, AggregateFunction function, Operator operator,
            Class<R> resultType, Expression<?> operand) {
        return new Aggregation<>(field, function, resultType,
                Expressions.numberOperation(resultType, operator, operand));
    }

    @SuppressWarnings("unchecked")
    private static <C extends Comparable<C>> Aggregation<C> extremum(
            String field, AggregateFunction function,
            Class<?> boxedFieldType, PathBuilder<?> pathBuilder) {
        Class<C> resultType = (Class<C>) boxedFieldType;
        Operator operator = function == AggregateFunction.MIN ? Ops.AggOps.MIN_AGG : Ops.AggOps.MAX_AGG;
        return new Aggregation<>(field, function, resultType, Expressions.comparableOperation(
                resultType, operator, comparablePath(pathBuilder, field, boxedFieldType)));
    }

    @SuppressWarnings("unchecked")
    private static <A extends Number & Comparable<A>> NumberPath<A> numberPath(
            PathBuilder<?> pathBuilder, String fieldName, Class<?> boxedFieldType) {
        return pathBuilder.getNumber(fieldName, (Class<A>) boxedFieldType);
    }

    @SuppressWarnings("unchecked")
    private static <C extends Comparable<C>> ComparablePath<C> comparablePath(
            PathBuilder<?> pathBuilder, String fieldName, Class<?> boxedFieldType) {
        return pathBuilder.getComparable(fieldName, (Class<C>) boxedFieldType);
    }

}
