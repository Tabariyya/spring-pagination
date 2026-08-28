package com.tabariyya.aggregation;

import com.querydsl.core.types.Predicate;
import com.querydsl.jpa.impl.JPAQuery;
import org.springframework.context.annotation.Scope;
import org.springframework.context.annotation.ScopedProxyMode;
import org.springframework.stereotype.Component;
import org.springframework.web.context.WebApplicationContext;

import java.util.List;

@Component
@Scope(value = WebApplicationContext.SCOPE_REQUEST, proxyMode = ScopedProxyMode.TARGET_CLASS)
public class AggregationRequest<TEntity> {

    private AggregationQuery aggregationQuery = AggregationQuery.of(AggregationSpec.EMPTY, GroupBySpec.EMPTY);
    private Predicate filter;

    public List<AggregationGroup> fetchGroups(JPAQuery<TEntity> query) {
        return aggregationQuery.fetchGroups(filtered(query));
    }

    private JPAQuery<TEntity> filtered(JPAQuery<TEntity> query) {
        return filter == null ? query : query.clone().where(filter);
    }

    public void setAggregationQuery(AggregationQuery aggregationQuery) {
        this.aggregationQuery = aggregationQuery == null
                ? AggregationQuery.of(AggregationSpec.EMPTY, GroupBySpec.EMPTY)
                : aggregationQuery;
    }

    public void setFilter(Predicate filter) {
        this.filter = filter;
    }
}
