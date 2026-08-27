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
                    .description("JSON-encoded aggregates over the whole filtered set, keyed by the alias to report each "
                            + "under, e.g. {\"total\":{\"$sum\":\"$score\"},\"average\":{\"$avg\":\"$score\"},"
                            + "\"rows\":{\"$count\":{}}}; supports $sum, $avg, $min, $max, $count and $countDistinct, "
                            + "and only fields present in the response body are allowed")
                    .in("query")
                    .required(false)
                    .schema(new Schema<String>().type("string")));

            operation.addParametersItem(new Parameter()
                    .name("groupBy")
                    .description("Field name, comma-separated list, or JSON array of field names to group by, "
                            + "e.g. score or [\"score\",\"activityId\"]; the aggregates are then computed per group and the "
                            + "endpoint returns a list of {key, values} groups instead of a single object")
                    .in("query")
                    .required(false)
                    .schema(new Schema<String>().type("string")));

            return operation;
        };
    }
}
