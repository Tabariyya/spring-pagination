package com.tabariyya.pagination;

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
            groups = aggregationQuery.fetchGroups(query);
        } else {
            values = aggregationQuery.fetch(query);
        }
    }

    public Map<String, Object> fetch(JPAQuery<TEntity> query) {
        values = aggregationQuery.fetch(query);
        return values;
    }

    public List<Group> fetchGroups(JPAQuery<TEntity> query) {
        groups = aggregationQuery.fetchGroups(query);
        return groups;
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
