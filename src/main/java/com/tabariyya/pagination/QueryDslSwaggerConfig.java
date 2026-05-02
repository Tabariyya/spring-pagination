package com.tabariyya.pagination;

import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.parameters.Parameter;
import org.springdoc.core.customizers.OperationCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.HandlerMethod;

import java.util.Arrays;
import java.util.List;
import java.util.stream.IntStream;

@Configuration
public class QueryDslSwaggerConfig {

    @Bean
    public OperationCustomizer hideQueryDslContextHolderByType() {
        return (Operation operation, HandlerMethod handlerMethod) -> {
            Class<?>[] parameterTypes = handlerMethod.getMethod().getParameterTypes();
            List<Parameter> swaggerParams = operation.getParameters();

            if (swaggerParams != null) {
                IntStream.range(0, parameterTypes.length)
                        .filter(i -> QuerySpec.class.isAssignableFrom(parameterTypes[i]))
                        .forEach(index -> {
                            if (index < swaggerParams.size()) {
                                swaggerParams.remove(index);
                            }
                        });
            }

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
                    .description("JSON-encoded filter criteria")
                    .in("query")
                    .required(false)
                    .schema(new Schema<String>().type("string")));

            operation.addParametersItem(new Parameter()
                    .name("ordering")
                    .description("JSON-encoded sort specification")
                    .in("query")
                    .required(false)
                    .schema(new Schema<String>().type("string")));

            operation.addParametersItem(new Parameter()
                    .name("skip")
                    .description("Skip (integer)")
                    .in("query")
                    .required(false)
                    .schema(new Schema<Integer>().type("integer")));

            operation.addParametersItem(new Parameter()
                    .name("size")
                    .description("Size (integer)")
                    .in("query")
                    .required(false)
                    .schema(new Schema<Integer>().type("integer")));

            return operation;
        };
    }
}

