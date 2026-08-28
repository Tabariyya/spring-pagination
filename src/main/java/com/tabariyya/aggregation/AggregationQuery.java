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

    public static AggregationQuery of(AggregationSpec aggregations) {
        return new AggregationQuery(aggregations, GroupBySpec.EMPTY, DEFAULT_MAX_GROUPS);
    }

    public static AggregationQuery of(AggregationSpec aggregations, GroupBySpec groupBy) {
        return new AggregationQuery(aggregations, groupBy, DEFAULT_MAX_GROUPS);
    }

    public static Builder builder(Class<?> entity) {
        return new Builder(entity);
    }

    public AggregationQuery withMaxGroups(int maxGroups) {
        if (maxGroups < 1) {
            throw new IllegalArgumentException("maxGroups must be at least 1");
        }
        return new AggregationQuery(aggregations, groupBy, maxGroups);
    }

    public boolean isGrouped() {
        return !groupBy.isEmpty();
    }

    public boolean isEmpty() {
        return aggregations.isEmpty() && groupBy.isEmpty();
    }

    public AggregationSpec aggregations() {
        return aggregations;
    }

    public GroupBySpec groupBy() {
        return groupBy;
    }

    public int maxGroups() {
        return maxGroups;
    }

    public Map<String, Object> fetch(JPAQuery<?> query) {
        if (isGrouped()) {
            throw new IllegalStateException(
                    "This aggregation groups by " + String.join(", ", groupByFieldNames())
                            + "; call fetchGroups instead");
        }
        if (aggregations.isEmpty()) {
            return Collections.emptyMap();
        }
        Tuple tuple = query.clone().select(aggregations.expressions()).fetchOne();
        return tuple == null ? Collections.emptyMap() : aggregations.read(tuple);
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

    private List<String> groupByFieldNames() {
        List<String> names = new ArrayList<>(groupBy.keys().size());
        for (GroupKey<?> key : groupBy.keys()) {
            names.add(key.field());
        }
        return names;
    }

    public static final class Builder {

        private final Class<?> entity;
        private final PathBuilder<?> pathBuilder;
        private final List<Aggregation<?>> aggregations = new ArrayList<>();
        private final List<GroupKey<?>> keys = new ArrayList<>();
        private final Set<String> aliases = new LinkedHashSet<>();
        private final Set<String> groupedFields = new LinkedHashSet<>();
        private int maxGroups = DEFAULT_MAX_GROUPS;

        private Builder(Class<?> entity) {
            this.entity = entity;
            this.pathBuilder = PathBuilders.of(entity);
        }

        public Builder sum(String alias, String field) {
            return add(AggregateFunction.SUM, alias, field);
        }

        public Builder avg(String alias, String field) {
            return add(AggregateFunction.AVG, alias, field);
        }

        public Builder min(String alias, String field) {
            return add(AggregateFunction.MIN, alias, field);
        }

        public Builder max(String alias, String field) {
            return add(AggregateFunction.MAX, alias, field);
        }

        public Builder countDistinct(String alias, String field) {
            return add(AggregateFunction.COUNT_DISTINCT, alias, field);
        }

        public Builder count(String alias) {
            return add(AggregateFunction.COUNT, alias, null);
        }

        public Builder groupBy(String... fields) {
            for (String field : fields) {
                String fieldName = stripPrefix(field);
                if (!groupedFields.add(fieldName)) {
                    throw new InvalidAggregationException("Group by names '" + fieldName + "' more than once");
                }
                try {
                    keys.add(AggregateExpressions.key(entity, pathBuilder, fieldName));
                } catch (NoSuchFieldException e) {
                    throw new GenericQueryDslException(e);
                }
            }
            return this;
        }

        public Builder maxGroups(int maxGroups) {
            if (maxGroups < 1) {
                throw new IllegalArgumentException("maxGroups must be at least 1");
            }
            this.maxGroups = maxGroups;
            return this;
        }

        public AggregationQuery build() {
            return new AggregationQuery(
                    aggregations.isEmpty() ? AggregationSpec.EMPTY : new AggregationSpec(aggregations),
                    keys.isEmpty() ? GroupBySpec.EMPTY : new GroupBySpec(keys),
                    maxGroups);
        }

        private Builder add(AggregateFunction function, String alias, String field) {
            if (alias == null || alias.isEmpty()) {
                throw new InvalidAggregationException("Aggregation alias must not be empty");
            }
            if (!aliases.add(alias)) {
                throw new InvalidAggregationException("Aggregation alias '" + alias + "' is used more than once");
            }
            try {
                aggregations.add(AggregateExpressions.build(
                        entity, pathBuilder, function, alias, field == null ? null : stripPrefix(field)));
            } catch (NoSuchFieldException e) {
                throw new GenericQueryDslException(e);
            }
            return this;
        }

        private static String stripPrefix(String field) {
            if (field == null || field.isEmpty()) {
                throw new InvalidAggregationException("Field name must not be empty");
            }
            return field.startsWith("$") ? field.substring(1) : field;
        }
    }
}
