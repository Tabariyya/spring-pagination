package com.tabariyya.pagination;

import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.parameters.Parameter;
import io.swagger.v3.oas.models.responses.ApiResponse;
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
public class QueryDslSwaggerConfig {

    @Bean
    public OperationCustomizer hideResolvedQueryParameters() {
        return (Operation operation, HandlerMethod handlerMethod) -> {
            List<Parameter> swaggerParams = operation.getParameters();
            if (swaggerParams == null) {
                return operation;
            }

            Set<String> resolvedNames = Arrays.stream(handlerMethod.getMethodParameters())
                    .filter(p -> QuerySpec.class.isAssignableFrom(p.getParameterType()))
                    .map(MethodParameter::getParameterName)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toSet());

            swaggerParams.removeIf(parameter -> resolvedNames.contains(parameter.getName()));

            return operation;
        };
    }

    @Bean
    public OperationCustomizer queryDslParamsCustomizer() {
        return (Operation operation, HandlerMethod handlerMethod) -> {

            boolean hasQuerySpecParam = Arrays.stream(handlerMethod.getMethodParameters())
                    .anyMatch(p -> QuerySpec.class.isAssignableFrom(p.getParameterType()));

            if (!hasQuerySpecParam) {
                return operation;
            }

            operation.addParametersItem(new Parameter()
                    .name("filters")
                    .description("JSON-encoded filter criteria; only fields present in the response body are allowed")
                    .in("query")
                    .required(false)
                    .schema(new Schema<String>().type("string")));

            operation.addParametersItem(new Parameter()
                    .name("ordering")
                    .description("JSON-encoded sort specification; only fields present in the response body are allowed")
                    .in("query")
                    .required(false)
                    .schema(new Schema<String>().type("string")));

            operation.addParametersItem(new Parameter()
                    .name("size")
                    .description("Page size (integer)")
                    .in("query")
                    .required(false)
                    .schema(new Schema<Integer>().type("integer")));

            operation.addParametersItem(new Parameter()
                    .name("cursor")
                    .description("Opaque cursor returned by the previous page; when present, filters/ordering/size are ignored")
                    .in("query")
                    .required(false)
                    .schema(new Schema<String>().type("string")));

            return operation;
        };
    }

    /**
     * Handlers with a QuerySpec parameter declare a List return type, but
     * PaginationResponseAdvice wraps it into a PaginatedResult at runtime;
     * this rewrites the documented 200 response to match.
     */
    @Bean
    public OperationCustomizer paginatedResultResponseCustomizer() {
        return (Operation operation, HandlerMethod handlerMethod) -> {
            boolean hasQuerySpecParam = Arrays.stream(handlerMethod.getMethodParameters())
                    .anyMatch(p -> QuerySpec.class.isAssignableFrom(p.getParameterType()));

            if (!hasQuerySpecParam || operation.getResponses() == null) {
                return operation;
            }

            ApiResponse okResponse = operation.getResponses().get("200");
            if (okResponse == null || okResponse.getContent() == null) {
                return operation;
            }

            okResponse.getContent().forEach((mediaTypeName, mediaType) -> {
                Schema<?> schema = mediaType.getSchema();
                if (schema == null || !"array".equals(schema.getType())) {
                    return;
                }
                Schema<Object> paginatedResult = new Schema<>().type("object");
                paginatedResult.addProperty("count", new Schema<Long>().type("integer").format("int64").nullable(true)
                        .description("Total rows matching the filter; null on cursor requests"));
                paginatedResult.addProperty("result", schema);
                paginatedResult.addProperty("nextCursor", new Schema<String>().type("string").nullable(true)
                        .description("Cursor for the next page; null when there are no further pages"));
                mediaType.setSchema(paginatedResult);
            });

            return operation;
        };
    }
}

