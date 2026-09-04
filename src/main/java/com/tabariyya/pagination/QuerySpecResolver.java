package com.tabariyya.pagination;

import com.querydsl.core.types.ExpressionUtils;
import com.querydsl.core.types.OrderSpecifier;
import com.querydsl.core.types.Predicate;
import org.springframework.core.MethodParameter;
import org.springframework.core.ResolvableType;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;
import org.springframework.stereotype.Component;

import org.springframework.http.ResponseEntity;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Collection;
import java.util.Map;

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

        String cursorToken = request.getParameter("cursor");

        String filter;
        String sort;
        String requestedSort;
        Integer size;
        boolean fromCursor = false;
        Map<String, Object> lastValues = null;

        if (cursorToken != null && !cursorToken.isEmpty()) {
            if (request.getParameter("filters") != null
                    || request.getParameter("ordering") != null
                    || request.getParameter("size") != null) {
                throw new CursorParameterConflictException(
                        "filters, ordering and size are not allowed together with a cursor; they are carried by the cursor itself");
            }
            Cursor cursor = CursorUtils.decode(cursorToken);
            filter = cursor.getFilters();
            sort = cursor.getOrdering();
            requestedSort = cursor.getRequestedOrdering();
            size = cursor.getSize();
            lastValues = cursor.getLastValues();
            querySpec.setCursorAttributes(cursor.getAttributes());
            querySpec.setCursorRequest(true);
            fromCursor = true;
        } else {
            filter = request.getParameter("filters");
            sort = request.getParameter("ordering");
            requestedSort = sort;
            size = parseSize(request.getParameter("size"));
            queryBuilderService.validateFieldsAgainstResponse(resolveResponseType(parameter, entity), filter, sort);
        }

        if (sort == null || sort.isEmpty()) {
            sort = "{\"id\":1}";
        }
        sort = queryBuilderService.ensureIdTieBreaker(sort);

        // Off a cursor it was normalized when the first page was served; off the query string it is
        // still url-encoded, and it is recorded the way getOrdering() reads.
        if (requestedSort != null && !requestedSort.isEmpty() && !fromCursor) {
            requestedSort = queryBuilderService.normalizeOrdering(requestedSort);
        }

        if (size == null) {
            size = 20;
        }

        Predicate filterPredicate = (filter != null && !filter.isEmpty())
                ? queryBuilderService.buildFilter(entity, filter)
                : null;
        Predicate keysetPredicate = (lastValues != null)
                ? queryBuilderService.buildKeysetPredicate(entity, sort, lastValues)
                : null;
        querySpec.setFilterQuery(ExpressionUtils.allOf(filterPredicate, keysetPredicate));

        OrderSpecifier<?>[] orderSpecifiers = queryBuilderService.buildOrderSpecifier(entity, sort);
        querySpec.setOrderingQuery(orderSpecifiers);

        querySpec.setLimit(size);
        querySpec.setEntityClass(entity);
        querySpec.setFilters(filter);
        querySpec.setOrdering(sort);
        querySpec.setRequestedOrdering(requestedSort);

        request.setAttribute(QuerySpec.class.getName(), querySpec);

        return querySpec;
    }

    private Integer parseSize(String size) {
        if (size == null || size.isEmpty()) {
            return null;
        }
        try {
            return Integer.parseInt(size);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid size parameter", e);
        }
    }

    /**
     * Resolves the element type of the handler's response body
     * (ResponseEntity&lt;List&lt;T&gt;&gt; or List&lt;T&gt;), which is what filter
     * and sort fields are validated against. Falls back to the entity when the
     * return type is not a list.
     */
    private Class<?> resolveResponseType(MethodParameter parameter, Class<?> entity) {
        if (parameter.getMethod() == null) {
            return entity;
        }
        ResolvableType returnType = ResolvableType.forMethodReturnType(parameter.getMethod(), parameter.getContainingClass());
        Class<?> raw = returnType.resolve();
        if (raw != null && ResponseEntity.class.isAssignableFrom(raw)) {
            returnType = returnType.getGeneric(0);
            raw = returnType.resolve();
        }
        if (raw != null && Collection.class.isAssignableFrom(raw)) {
            Class<?> elementType = returnType.getGeneric(0).resolve();
            if (elementType != null) {
                return elementType;
            }
        }
        return entity;
    }

    private Class<?> resolveGenericType(MethodParameter parameter) {
        ResolvableType resolvableType = ResolvableType.forMethodParameter(parameter);
        ResolvableType generic = resolvableType.getGeneric(0);
        Class<?> entity = generic.resolve();

        return entity;
    }
}
