package com.tabariyya.pagination;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.MethodParameter;
import org.springframework.core.ResolvableType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import java.util.Collection;
import java.util.Map;

@Component
public class AggregationRequestResolver implements HandlerMethodArgumentResolver {

    private final QueryBuilderService queryBuilderService;
    private final AggregationRequest<?> aggregationRequest;

    public AggregationRequestResolver(QueryBuilderService queryBuilderService,
                                      AggregationRequest<?> aggregationRequest) {
        this.queryBuilderService = queryBuilderService;
        this.aggregationRequest = aggregationRequest;
    }

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return AggregationRequest.class.isAssignableFrom(parameter.getParameterType());
    }

    @Override
    public Object resolveArgument(MethodParameter parameter,
                                  ModelAndViewContainer mavContainer,
                                  NativeWebRequest webRequest,
                                  WebDataBinderFactory binderFactory) {
        HttpServletRequest request = webRequest.getNativeRequest(HttpServletRequest.class);
        if (request == null) {
            return aggregationRequest;
        }
        Class<?> entity = resolveGenericType(parameter);
        if (entity == null) {
            return aggregationRequest;
        }

        String aggregations = request.getParameter("aggregations");
        String groupBy = request.getParameter("groupBy");

        boolean hasAggregations = aggregations != null && !aggregations.isEmpty();
        boolean hasGroupBy = groupBy != null && !groupBy.isEmpty();

        if (!hasAggregations && !hasGroupBy) {
            request.setAttribute(AggregationRequest.class.getName(), aggregationRequest);
            return aggregationRequest;
        }

        String cursor = request.getParameter("cursor");
        if (cursor != null && !cursor.isEmpty()) {
            throw new CursorParameterConflictException(
                    "aggregations and groupBy are not allowed together with a cursor; they describe the whole "
                            + "result set and are computed on the first request only");
        }

        Class<?> projection = resolveProjectionType(parameter, entity);
        queryBuilderService.validateAggregationsAgainstResponse(projection, aggregations);
        queryBuilderService.validateGroupByAgainstResponse(projection, groupBy);

        AggregationSpec aggregationSpec = hasAggregations
                ? queryBuilderService.buildAggregations(entity, aggregations)
                : AggregationSpec.EMPTY;
        GroupBySpec groupBySpec = hasGroupBy
                ? queryBuilderService.buildGroupBy(entity, groupBy)
                : GroupBySpec.EMPTY;

        aggregationRequest.setAggregationQuery(AggregationQuery.of(aggregationSpec, groupBySpec));
        request.setAttribute(AggregationRequest.class.getName(), aggregationRequest);

        return aggregationRequest;
    }

    private Class<?> resolveProjectionType(MethodParameter parameter, Class<?> entity) {
        AggregateOver aggregateOver = parameter.getParameterAnnotation(AggregateOver.class);
        if (aggregateOver != null) {
            return aggregateOver.value();
        }

        Class<?> responseType = resolveResponseElementType(parameter);
        if (responseType == null || responseType == Group.class || Map.class.isAssignableFrom(responseType)) {
            throw new InvalidAggregationException("Cannot tell which fields of " + entity.getSimpleName()
                    + " may be aggregated on this endpoint; annotate the AggregationRequest parameter with"
                    + " @AggregateOver(YourResponseDto.class)");
        }
        return responseType;
    }

    private Class<?> resolveResponseElementType(MethodParameter parameter) {
        if (parameter.getMethod() == null) {
            return null;
        }
        ResolvableType returnType =
                ResolvableType.forMethodReturnType(parameter.getMethod(), parameter.getContainingClass());
        Class<?> raw = returnType.resolve();
        if (raw != null && ResponseEntity.class.isAssignableFrom(raw)) {
            returnType = returnType.getGeneric(0);
            raw = returnType.resolve();
        }
        if (raw != null && Collection.class.isAssignableFrom(raw)) {
            return returnType.getGeneric(0).resolve();
        }
        return null;
    }

    private Class<?> resolveGenericType(MethodParameter parameter) {
        return ResolvableType.forMethodParameter(parameter).getGeneric(0).resolve();
    }
}
