package com.tabariyya.aggregation;

import com.tabariyya.pagination.FieldUtils;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.core.MethodParameter;
import org.springframework.core.ResolvableType;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.stream.Stream;

/**
 * Fails startup when an endpoint takes an {@link AggregationRequest} but gives no way to tell which
 * fields it may aggregate: no {@link AggregateOver} on the parameter, and no response type to read
 * them from. Such an endpoint would otherwise start fine and reject every request it receives. Each
 * field an {@link AggregateOver} names has to exist on the endpoint's entity, and a
 * {@code "com.acme.User#id"} constant has to name that entity.
 */
@Component
public class AggregationRequestVerifier implements SmartInitializingSingleton {

    private final List<RequestMappingHandlerMapping> handlerMappings;

    public AggregationRequestVerifier(List<RequestMappingHandlerMapping> handlerMappings) {
        this.handlerMappings = handlerMappings;
    }

    @Override
    public void afterSingletonsInstantiated() {
        List<String> violations = new ArrayList<>();
        for (RequestMappingHandlerMapping handlerMapping : handlerMappings) {
            violations.addAll(violations(handlerMapping.getHandlerMethods().values()));
        }

        if (!violations.isEmpty()) {
            throw new IllegalStateException(String.join("; ", violations));
        }
    }

    static List<String> violations(Collection<HandlerMethod> handlerMethods) {
        List<String> violations = new ArrayList<>();
        for (HandlerMethod handlerMethod : handlerMethods) {
            String endpoint = handlerMethod.getBeanType().getSimpleName() + "#" + handlerMethod.getMethod().getName();
            for (MethodParameter parameter : handlerMethod.getMethodParameters()) {
                if (!AggregationRequest.class.isAssignableFrom(parameter.getParameterType())) {
                    continue;
                }

                AggregateOver aggregateOver = parameter.getParameterAnnotation(AggregateOver.class);
                if (aggregateOver == null) {
                    if (AggregationRequestResolver.responseFieldSource(parameter) == null) {
                        violations.add(endpoint + " needs @AggregateOver on its AggregationRequest parameter");
                    }
                    continue;
                }

                Class<?> entity = ResolvableType.forMethodParameter(parameter).getGeneric(0).resolve();
                if (entity == null) {
                    continue;
                }
                Stream.of(aggregateOver.groupBy(), aggregateOver.aggregate(), aggregateOver.filter())
                        .flatMap(Arrays::stream)
                        .distinct()
                        .filter(reference -> !namesFieldOf(entity, reference))
                        .forEach(reference -> violations.add(endpoint + " names " + reference
                                + ", which is not a field of " + entity.getSimpleName()));
            }
        }
        return violations;
    }

    private static boolean namesFieldOf(Class<?> entity, String reference) {
        int separator = reference.indexOf('#');
        if (separator >= 0 && !reference.substring(0, separator).equals(entity.getCanonicalName())) {
            return false;
        }
        try {
            FieldUtils.findField(entity, reference.substring(separator + 1));
            return true;
        } catch (NoSuchFieldException e) {
            return false;
        }
    }
}
