package com.tabariyya.aggregation;

import com.tabariyya.pagination.CursorParameterConflictException;
import com.tabariyya.pagination.QueryBuilderService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.MethodParameter;
import org.springframework.core.ResolvableType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

@Component
public class AggregationRequestResolver implements HandlerMethodArgumentResolver {

    private final QueryBuilderService queryBuilderService;
    private final AggregationQueryBuilder aggregationQueryBuilder;
    private final AggregationRequest<?> aggregationRequest;

    public AggregationRequestResolver(QueryBuilderService queryBuilderService,
                                      AggregationQueryBuilder aggregationQueryBuilder,
                                      AggregationRequest<?> aggregationRequest) {
        this.queryBuilderService = queryBuilderService;
        this.aggregationQueryBuilder = aggregationQueryBuilder;
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
        String filters = request.getParameter("filters");

        boolean hasAggregations = aggregations != null && !aggregations.isEmpty();
        boolean hasGroupBy = groupBy != null && !groupBy.isEmpty();
        boolean hasFilters = filters != null && !filters.isEmpty();

        if (!hasAggregations && !hasGroupBy && !hasFilters) {
            return aggregationRequest;
        }

        String cursor = request.getParameter("cursor");
        if (cursor != null && !cursor.isEmpty()) {
            throw new CursorParameterConflictException(
                    "aggregations and groupBy are not allowed together with a cursor; they describe the whole "
                            + "result set and are computed on the first request only");
        }

        AggregateOver aggregateOver = parameter.getParameterAnnotation(AggregateOver.class);
        if (aggregateOver != null) {
            aggregationQueryBuilder.validateAggregationsAgainstFields(aggregateOver.aggregate(), aggregations);
            aggregationQueryBuilder.validateGroupByAgainstFields(aggregateOver.groupBy(), groupBy);
            if (hasFilters) {
                aggregationQueryBuilder.validateFiltersAgainstFields(filterFields(aggregateOver), filters);
            }
        } else {
            Class<?> projection = resolveProjectionType(parameter, entity);
            aggregationQueryBuilder.validateAggregationsAgainstResponse(projection, aggregations);
            aggregationQueryBuilder.validateGroupByAgainstResponse(projection, groupBy);
            if (hasFilters) {
                queryBuilderService.validateFieldsAgainstResponse(projection, filters, null);
            }
        }

        AggregationSpec aggregationSpec = hasAggregations
                ? aggregationQueryBuilder.buildAggregations(entity, aggregations)
                : AggregationSpec.EMPTY;
        GroupBySpec groupBySpec = hasGroupBy
                ? aggregationQueryBuilder.buildGroupBy(entity, groupBy)
                : GroupBySpec.EMPTY;

        aggregationRequest.setAggregationQuery(AggregationQuery.of(aggregationSpec, groupBySpec));
        aggregationRequest.setFilter(hasFilters ? queryBuilderService.buildFilter(entity, filters) : null);

        return aggregationRequest;
    }

    private String[] filterFields(AggregateOver aggregateOver) {
        if (aggregateOver.filter().length > 0) {
            return aggregateOver.filter();
        }
        Set<String> merged = new LinkedHashSet<>(Arrays.asList(aggregateOver.groupBy()));
        merged.addAll(Arrays.asList(aggregateOver.aggregate()));
        return merged.toArray(new String[0]);
    }

    private Class<?> resolveProjectionType(MethodParameter parameter, Class<?> entity) {
        Class<?> responseType = resolveResponseElementType(parameter);
        if (responseType == null || responseType == AggregationGroup.class || Map.class.isAssignableFrom(responseType)) {
            throw new InvalidAggregationException("Cannot tell which fields of " + entity.getSimpleName()
                    + " may be aggregated on this endpoint; annotate the AggregationRequest parameter with"
                    + " @AggregateOver(groupBy = {...}, aggregate = {...})");
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
