package com.tabariyya.aggregation;

import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.parameters.Parameter;
import com.tabariyya.pagination.QuerySpec;
import org.springdoc.core.customizers.OperationCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.MethodParameter;
import org.springframework.web.method.HandlerMethod;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Configuration
public class AggregationSwaggerConfig {

    @Bean
    public OperationCustomizer hideAggregationRequestParameter() {
        return (Operation operation, HandlerMethod handlerMethod) -> {
            List<Parameter> swaggerParams = operation.getParameters();
            if (swaggerParams == null) {
                return operation;
            }

            Set<String> resolvedNames = Arrays.stream(handlerMethod.getMethodParameters())
                    .filter(p -> AggregationRequest.class.isAssignableFrom(p.getParameterType()))
                    .map(MethodParameter::getParameterName)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toSet());

            swaggerParams.removeIf(parameter -> resolvedNames.contains(parameter.getName()));

            return operation;
        };
    }

    @Bean
    public OperationCustomizer aggregationParamsCustomizer() {
        return (Operation operation, HandlerMethod handlerMethod) -> {

            boolean hasAggregationParam = Arrays.stream(handlerMethod.getMethodParameters())
                    .anyMatch(p -> AggregationRequest.class.isAssignableFrom(p.getParameterType()));

            if (!hasAggregationParam) {
                return operation;
            }

            boolean hasQuerySpecParam = Arrays.stream(handlerMethod.getMethodParameters())
                    .anyMatch(p -> QuerySpec.class.isAssignableFrom(p.getParameterType()));

            if (!hasQuerySpecParam) {
                operation.addParametersItem(new Parameter()
                        .name("filters")
                        .description("JSON-encoded filter criteria applied before aggregating; only fields the "
                                + "endpoint allows may be used")
                        .in("query")
                        .required(false)
                        .schema(new Schema<String>().type("string")));
            }

            operation.addParametersItem(new Parameter()
                    .name("aggregations")
                    .description("JSON-encoded aggregation, in MongoDB $group form: \"$groupBy\" names the field or "
                            + "fields to group by and every other key is an alias for an accumulator, e.g. "
                            + "{\"$groupBy\":\"$score\",\"average\":{\"$avg\":\"$score\"},\"rows\":{\"$count\":{}}}. "
                            + "\"$groupBy\" accepts a field, a comma-separated list, or an array. Supports $sum, $avg, "
                            + "$min, $max, $count and $countDistinct, and only fields the endpoint permits")
                    .in("query")
                    .required(false)
                    .schema(new Schema<String>().type("string")));

            return operation;
        };
    }
}
