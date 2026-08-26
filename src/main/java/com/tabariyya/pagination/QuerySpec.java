package com.tabariyya.pagination;

import com.querydsl.core.Tuple;
import com.querydsl.core.types.EntityPath;
import com.querydsl.core.types.OrderSpecifier;
import com.querydsl.core.types.Predicate;
import com.querydsl.core.types.dsl.Expressions;
import com.querydsl.core.types.dsl.NumberExpression;
import com.querydsl.jpa.impl.JPAQuery;
import org.springframework.context.annotation.Scope;
import org.springframework.context.annotation.ScopedProxyMode;
import org.springframework.stereotype.Component;
import org.springframework.web.context.WebApplicationContext;

import java.util.ArrayList;
import java.util.List;

@Component
@Scope(value = WebApplicationContext.SCOPE_REQUEST, proxyMode = ScopedProxyMode.TARGET_CLASS)
public class QuerySpec<TEntity> {
    private Predicate predicate;
    private OrderSpecifier<?>[] orderSpecifiers;
    private Integer limit;
    private Class<?> entityClass;
    private String filters;
    private String ordering;
    private Object lastRow;
    private boolean cursorRequest;
    private Long count;

    /**
     * Applies the ordering and limit to the given query, fetches the page and
     * remembers the last row so {@link #getNextCursor()} can build the cursor
     * for the next page. On the first request the total count is fetched in the
     * same query via a window function and exposed through {@link #getCount()};
     * cursor requests skip it.
     */
    @SuppressWarnings("unchecked")
    public List<TEntity> fetchPage(JPAQuery<TEntity> query) {
        query.orderBy(orderSpecifiers).limit(limit);

        if (cursorRequest) {
            List<TEntity> rows = query.fetch();
            lastRow = rows.isEmpty() ? null : rows.get(rows.size() - 1);
            return rows;
        }

        EntityPath<TEntity> root = (EntityPath<TEntity>) query.getMetadata().getJoins().get(0).getTarget();
        NumberExpression<Long> totalCount = Expressions.numberTemplate(Long.class, "count(*) over ()");
        List<Tuple> tuples = query.select(root, totalCount).fetch();

        count = tuples.isEmpty() ? 0L : tuples.get(0).get(totalCount);
        List<TEntity> rows = new ArrayList<>(tuples.size());
        for (Tuple tuple : tuples) {
            rows.add(tuple.get(root));
        }
        lastRow = rows.isEmpty() ? null : rows.get(rows.size() - 1);
        return rows;
    }

    /**
     * Total count of rows matching the filter, set by {@link #fetchPage} on the
     * first request; null on cursor requests.
     */
    public Long getCount() {
        return count;
    }

    /**
     * Returns the cursor for the next page, or null when the last fetched page
     * was empty (no further pages). Must be called after {@link #fetchPage}.
     */
    public String getNextCursor() {
        return CursorUtils.nextCursor(this, lastRow);
    }

    public Predicate getFilterQuery() {
        return predicate;
    }
    public void setFilterQuery(Predicate predicate) {
        this.predicate = predicate;
    }

    public OrderSpecifier<?>[] getOrderingQuery() {
        return orderSpecifiers;
    }
    public void setOrderingQuery(OrderSpecifier<?>[] orderSpecifiers) {
        this.orderSpecifiers = orderSpecifiers;
    }

    public Integer getLimit() {
        return limit;
    }
    public void setLimit(Integer limit) {
        this.limit = limit;
    }

    public Class<?> getEntityClass() {
        return entityClass;
    }
    public void setEntityClass(Class<?> entityClass) {
        this.entityClass = entityClass;
    }

    public String getFilters() {
        return filters;
    }
    public void setFilters(String filters) {
        this.filters = filters;
    }

    public String getOrdering() {
        return ordering;
    }
    public void setOrdering(String ordering) {
        this.ordering = ordering;
    }

    public boolean isCursorRequest() {
        return cursorRequest;
    }
    public void setCursorRequest(boolean cursorRequest) {
        this.cursorRequest = cursorRequest;
    }
}
