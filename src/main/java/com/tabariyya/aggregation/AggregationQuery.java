package com.tabariyya.aggregation;

import com.tabariyya.pagination.PathBuilders;
import com.tabariyya.pagination.GenericQueryDslException;
import com.querydsl.core.Tuple;
import com.querydsl.core.types.Expression;
import com.querydsl.core.types.Ops;
import com.querydsl.core.types.dsl.Expressions;
import com.querydsl.core.types.dsl.NumberExpression;
import com.querydsl.core.types.dsl.PathBuilder;
import com.querydsl.jpa.impl.JPAQuery;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class AggregationQuery {

    public static final int DEFAULT_MAX_GROUPS = 1000;

    private final AggregationSpec aggregations;
    private final GroupBySpec groupBy;
    private final int maxGroups;

    private AggregationQuery(AggregationSpec aggregations, GroupBySpec groupBy, int maxGroups) {
        this.aggregations = aggregations == null ? AggregationSpec.EMPTY : aggregations;
        this.groupBy = groupBy == null ? GroupBySpec.EMPTY : groupBy;
        this.maxGroups = maxGroups;
    }


    public static AggregationQuery of(AggregationSpec aggregations, GroupBySpec groupBy) {
        return new AggregationQuery(aggregations, groupBy, DEFAULT_MAX_GROUPS);
    }



    public boolean isGrouped() {
        return !groupBy.isEmpty();
    }

    public boolean isEmpty() {
        return aggregations.isEmpty() && groupBy.isEmpty();
    }





    public List<AggregationGroup> fetchGroups(JPAQuery<?> query) {
        if (!isGrouped()) {
            throw new IllegalStateException("This aggregation has no group by; call fetch instead");
        }

        Expression<?>[] keyExpressions = groupBy.expressions();
        Expression<?>[] valueExpressions = aggregations.expressions();
        NumberExpression<Long> rowCount = Expressions.numberOperation(Long.class, Ops.AggOps.COUNT_ALL_AGG);

        Expression<?>[] projection = new Expression<?>[keyExpressions.length + valueExpressions.length + 1];
        System.arraycopy(keyExpressions, 0, projection, 0, keyExpressions.length);
        System.arraycopy(valueExpressions, 0, projection, keyExpressions.length, valueExpressions.length);
        projection[projection.length - 1] = rowCount;

        List<Tuple> tuples = query.clone()
                .select(projection)
                .groupBy(keyExpressions)
                .orderBy(groupBy.ordering())
                .limit(maxGroups + 1L)
                .fetch();

        if (tuples.size() > maxGroups) {
            throw new InvalidAggregationException("Group by produced more than " + maxGroups
                    + " groups; narrow the filter or group by fewer fields");
        }

        long grandTotal = 0L;
        for (Tuple tuple : tuples) {
            Long total = tuple.get(rowCount);
            grandTotal += total == null ? 0L : total;
        }

        List<AggregationGroup> groups = new ArrayList<>(tuples.size());
        for (Tuple tuple : tuples) {
            Long total = tuple.get(rowCount);
            long rows = total == null ? 0L : total;

            List<AggregationEntry> data = new ArrayList<>(groupBy.entries(tuple));
            data.addAll(aggregations.entries(tuple));

            groups.add(new AggregationGroup(data, percentage(rows, grandTotal), rows));
        }
        return groups;
    }

    private static double percentage(long rows, long grandTotal) {
        if (grandTotal == 0L) {
            return 0d;
        }
        return Math.round(rows * 10000d / grandTotal) / 100d;
    }

}
