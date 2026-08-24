package com.tabariyya.pagination;

import com.querydsl.core.types.Expression;
import com.querydsl.core.types.Operator;
import com.querydsl.core.types.Ops;
import com.querydsl.core.types.dsl.ComparablePath;
import com.querydsl.core.types.dsl.Expressions;
import com.querydsl.core.types.dsl.NumberPath;
import com.querydsl.core.types.dsl.PathBuilder;

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

    static Class<?> box(Class<?> type) {
        return type.isPrimitive() ? BOXED_PRIMITIVES.get(type) : type;
    }

    static Aggregation<Long> count(String alias) {
        return new Aggregation<>(alias, Long.class,
                Expressions.numberOperation(Long.class, Ops.AggOps.COUNT_ALL_AGG));
    }

    static Aggregation<Long> countDistinct(String alias, PathBuilder<?> pathBuilder, String fieldName) {
        return new Aggregation<>(alias, Long.class, Expressions.numberOperation(
                Long.class, Ops.AggOps.COUNT_DISTINCT_AGG, pathBuilder.get(fieldName)));
    }

    static Aggregation<?> sum(String alias, PathBuilder<?> pathBuilder, String fieldName, Class<?> boxedFieldType) {
        NumberPath<?> path = numberPath(pathBuilder, fieldName, boxedFieldType);
        if (boxedFieldType == BigDecimal.class) {
            return numberAggregation(alias, Ops.AggOps.SUM_AGG, BigDecimal.class, path);
        }
        if (boxedFieldType == BigInteger.class) {
            return numberAggregation(alias, Ops.AggOps.SUM_AGG, BigInteger.class, path);
        }
        if (boxedFieldType == Double.class || boxedFieldType == Float.class) {
            return numberAggregation(alias, Ops.AggOps.SUM_AGG, Double.class, path);
        }
        return numberAggregation(alias, Ops.AggOps.SUM_AGG, Long.class, path);
    }

    static Aggregation<Double> avg(String alias, PathBuilder<?> pathBuilder, String fieldName, Class<?> boxedFieldType) {
        return numberAggregation(alias, Ops.AggOps.AVG_AGG, Double.class,
                numberPath(pathBuilder, fieldName, boxedFieldType));
    }

    static Aggregation<?> extremum(String alias, Operator operator, PathBuilder<?> pathBuilder,
                                   String fieldName, Class<?> boxedFieldType) {
        return comparableAggregation(alias, operator, boxedFieldType,
                comparablePath(pathBuilder, fieldName, boxedFieldType));
    }

    private static <R extends Number & Comparable<R>> Aggregation<R> numberAggregation(
            String alias, Operator operator, Class<R> resultType, Expression<?> operand) {
        return new Aggregation<>(alias, resultType, Expressions.numberOperation(resultType, operator, operand));
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

    @SuppressWarnings("unchecked")
    private static <C extends Comparable<C>> Aggregation<C> comparableAggregation(
            String alias, Operator operator, Class<?> boxedFieldType, Expression<?> operand) {
        Class<C> resultType = (Class<C>) boxedFieldType;
        return new Aggregation<>(alias, resultType,
                Expressions.comparableOperation(resultType, operator, operand));
    }
}
