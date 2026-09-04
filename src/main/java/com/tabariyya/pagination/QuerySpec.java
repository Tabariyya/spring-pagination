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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
@Scope(value = WebApplicationContext.SCOPE_REQUEST, proxyMode = ScopedProxyMode.TARGET_CLASS)
public class QuerySpec<TEntity> {
    private Predicate predicate;
    private OrderSpecifier<?>[] orderSpecifiers;
    private Integer limit;
    private Class<?> entityClass;
    private String filters;
    private String ordering;
    private String requestedOrdering;
    private Object lastRow;
    private boolean cursorRequest;
    private Long count;
    private final Map<String, Object> attributes = new LinkedHashMap<>();

    /**
     * Applies the filter, the ordering and the limit to the given query, fetches
     * the page and remembers the last row so {@link #getNextCursor()} can build
     * the cursor for the next page. The filter also carries the keyset condition
     * of a cursor request, so callers must not skip it. On the first request the
     * total count is fetched in the same query via a window function and exposed
     * through {@link #getCount()}; cursor requests skip it.
     */
    @SuppressWarnings("unchecked")
    public List<TEntity> fetchPage(JPAQuery<TEntity> query) {
        query.where(predicate).orderBy(orderSpecifiers).limit(limit);

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

    /**
     * The ordering actually applied: always non-null, always ending in the id tie-breaker, and equal
     * to {@code {"id":1}} when the caller asked for no ordering at all. Use {@link #hasOrdering()} to
     * tell that default apart from an ordering someone asked for.
     */
    public String getOrdering() {
        return ordering;
    }
    public void setOrdering(String ordering) {
        this.ordering = ordering;
    }

    /**
     * Whether the caller asked for an ordering of their own.
     *
     * <p>{@link #getOrdering()} cannot answer this: it is never null, because a request without an
     * ordering is given {@code {"id":1}} so that paging has a stable sort, and one with an ordering
     * has the same id appended as a tie-breaker. Both end up mentioning id, so the string alone does
     * not say who asked for it. This does, and it keeps saying it on the later pages of a cursor.
     *
     * <p>An explicitly requested {@code {"id":1}} counts as an ordering: the caller named it, even
     * though it matches what the default would have been.
     */
    public boolean hasOrdering() {
        return requestedOrdering != null;
    }

    /**
     * The ordering as the caller wrote it, or null when they asked for none - the raw form behind
     * {@link #hasOrdering()}, for callers that want to look at what was asked for.
     */
    public String getRequestedOrdering() {
        return requestedOrdering;
    }
    public void setRequestedOrdering(String requestedOrdering) {
        this.requestedOrdering = (requestedOrdering == null || requestedOrdering.isBlank())
                ? null
                : requestedOrdering;
    }

    /**
     * Stores an application-defined value that travels with the pagination
     * cursor: whatever is on the spec when the response is written is encoded
     * into the next cursor and restored here on the follow-up request. Values
     * must be JSON-serializable, and the cursor is client-visible, so keep them
     * small and never put anything secret in one.
     */
    public void setCursorAttribute(String key, Object value) {
        attributes.put(key, value);
    }

    /**
     * Returns the raw attribute as it currently stands. On a cursor request the
     * value comes back from JSON, so a non-scalar arrives as a Map or List;
     * use {@link #getCursorAttribute(String, Class)} to get the original type back.
     */
    public Object getCursorAttribute(String key) {
        return attributes.get(key);
    }

    /**
     * Returns the attribute converted to the given type, or null when it is absent.
     */
    public <T> T getCursorAttribute(String key, Class<T> type) {
        return CursorUtils.convertAttribute(key, attributes.get(key), type);
    }

    public Map<String, Object> getCursorAttributes() {
        return attributes;
    }

    /**
     * Replaces all attributes; used by the resolver to seed them from an incoming cursor.
     */
    public void setCursorAttributes(Map<String, Object> newAttributes) {
        attributes.clear();
        if (newAttributes != null) {
            attributes.putAll(newAttributes);
        }
    }

    public boolean isCursorRequest() {
        return cursorRequest;
    }
    public void setCursorRequest(boolean cursorRequest) {
        this.cursorRequest = cursorRequest;
    }
}
