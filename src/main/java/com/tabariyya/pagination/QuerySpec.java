package com.tabariyya.pagination;

import com.querydsl.core.types.OrderSpecifier;
import com.querydsl.core.types.Predicate;
import org.springframework.context.annotation.Scope;
import org.springframework.context.annotation.ScopedProxyMode;
import org.springframework.stereotype.Component;
import org.springframework.web.context.WebApplicationContext;

@Component
@Scope(value = WebApplicationContext.SCOPE_REQUEST, proxyMode = ScopedProxyMode.TARGET_CLASS)
public class QuerySpec<TEntity> {
    private Predicate predicate;
    private OrderSpecifier<?>[] orderSpecifiers;
    private Integer offset;
    private Integer limit;

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

    public Integer getOffset() {
        return offset;
    }
    public void setOffset(Integer offset) {
        this.offset = offset;
    }

    public Integer getLimit() {
        return limit;
    }
    public void setLimit(Integer limit) {
        this.limit = limit;
    }
}
