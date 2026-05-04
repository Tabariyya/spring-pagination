package com.tabariyya.pagination;

import com.querydsl.core.types.ExpressionUtils;
import com.querydsl.core.types.OrderSpecifier;
import com.querydsl.core.types.Predicate;
import org.reflections.Reflections;
import org.springframework.core.MethodParameter;
import org.springframework.core.ResolvableType;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;
import org.springframework.stereotype.Component;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Set;

@Component
public class QuerySpecResolver implements HandlerMethodArgumentResolver {

    private final QueryBuilderService queryBuilderService;
    private final QuerySpec<?> querySpec;

    public QuerySpecResolver(QueryBuilderService queryBuilderService,
                             QuerySpec<?> querySpec) {
        this.queryBuilderService = queryBuilderService;
        this.querySpec = querySpec;
    }

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return QuerySpec.class.isAssignableFrom(parameter.getParameterType());
    }

    @Override
    public Object resolveArgument(MethodParameter parameter,
                                  ModelAndViewContainer mavContainer,
                                  NativeWebRequest webRequest,
                                  WebDataBinderFactory binderFactory) {
        HttpServletRequest request = webRequest.getNativeRequest(HttpServletRequest.class);
        if (request == null) {
            return querySpec;
        }
        Class<?> entity = resolveGenericType(parameter);
        if (entity == null) {
            return querySpec;
        }

        String filter = request.getParameter("filters");
        String sort = request.getParameter("ordering");
        String offset = request.getParameter("skip");
        String limit = request.getParameter("size");

        if(filter != null && !filter.isEmpty()) {
            Predicate predicate = queryBuilderService.buildFilter(entity, filter);
            querySpec.setFilterQuery(predicate);
        } else {
            querySpec.setFilterQuery(ExpressionUtils.allOf());
        }

        if(sort == null || sort.isEmpty()) {
            sort = "{\"id\":1}";
        }

        OrderSpecifier<?>[] orderSpecifiers = queryBuilderService.buildOrderSpecifier(entity, sort);
        querySpec.setOrderingQuery(orderSpecifiers);

        if(offset == null || offset.isEmpty()) {
            offset = "0";
        }

        if (limit == null || limit.isEmpty()) {
            limit = "20";
        }

        try {
            int pageOffset = Integer.parseInt(offset);
            int pageLimit = Integer.parseInt(limit);
            querySpec.setOffset(pageOffset);
            querySpec.setLimit(pageLimit);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid page or limit parameter", e);
        }

        request.setAttribute(QuerySpec.class.getName(), querySpec);

        return querySpec;
    }

    private Class<?> resolveGenericType(MethodParameter parameter) {
        ResolvableType resolvableType = ResolvableType.forMethodParameter(parameter);
        ResolvableType generic = resolvableType.getGeneric(0);
        Class<?> entity = generic.resolve();

        Reflections reflections = new Reflections("com.waleed");
        Set<? extends Class<?>> subTypes = reflections.getSubTypesOf(entity);

        for (Class<?> clazz : subTypes) {
            return clazz;
        }
        return entity;
    }
}
