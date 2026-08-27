package com.tabariyya.pagination;

import com.querydsl.core.types.Predicate;
import com.querydsl.jpa.impl.JPAQuery;
import org.springframework.context.annotation.Scope;
import org.springframework.context.annotation.ScopedProxyMode;
import org.springframework.stereotype.Component;
import org.springframework.web.context.WebApplicationContext;

import java.util.List;
import java.util.Map;

@Component
@Scope(value = WebApplicationContext.SCOPE_REQUEST, proxyMode = ScopedProxyMode.TARGET_CLASS)
public class AggregationRequest<TEntity> {

    private AggregationQuery aggregationQuery = AggregationQuery.of(AggregationSpec.EMPTY);
    private Predicate filter;
    private Map<String, Object> values;
    private List<Group> groups;

    public boolean isEmpty() {
        return aggregationQuery.isEmpty();
    }

    public boolean isGrouped() {
        return aggregationQuery.isGrouped();
    }

    public void run(JPAQuery<TEntity> query) {
        if (aggregationQuery.isEmpty()) {
            return;
        }
        if (aggregationQuery.isGrouped()) {
            fetchGroups(query);
        } else {
            fetch(query);
        }
    }

    public Map<String, Object> fetch(JPAQuery<TEntity> query) {
        values = aggregationQuery.fetch(filtered(query));
        return values;
    }

    public List<Group> fetchGroups(JPAQuery<TEntity> query) {
        groups = aggregationQuery.fetchGroups(filtered(query));
        return groups;
    }

    private JPAQuery<TEntity> filtered(JPAQuery<TEntity> query) {
        return filter == null ? query : query.clone().where(filter);
    }

    public Predicate getFilter() {
        return filter;
    }

    public void setFilter(Predicate filter) {
        this.filter = filter;
    }

    public Map<String, Object> getValues() {
        return values;
    }

    public List<Group> getGroups() {
        return groups;
    }

    public AggregationQuery getAggregationQuery() {
        return aggregationQuery;
    }

    public void setAggregationQuery(AggregationQuery aggregationQuery) {
        this.aggregationQuery = aggregationQuery == null
                ? AggregationQuery.of(AggregationSpec.EMPTY)
                : aggregationQuery;
    }
}
