package com.tabariyya.aggregation;

import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.core.MethodParameter;
import org.springframework.core.ResolvableType;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Fails startup when an endpoint takes an {@link AggregationRequest} but gives no way to tell which
 * fields it may aggregate: no {@link AggregateOver} on the parameter, and no response type to read
 * them from. Such an endpoint would otherwise start fine and reject every request it receives. Every
 * value of an {@link AggregateOver} has to be a {@code "com.acme.User#id"} reference, as dto-generator's
 * {@code @Fields} constants are, to a field of the endpoint's own entity.
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
                Set<String> fieldReferences = fieldReferences(entity);
                Stream.of(aggregateOver.groupBy(), aggregateOver.aggregate(), aggregateOver.filter())
                        .flatMap(Arrays::stream)
                        .distinct()
                        .filter(reference -> !fieldReferences.contains(reference))
                        .forEach(reference -> violations.add(endpoint + " names " + reference
                                + ", which is not a field reference of " + entity.getSimpleName()
                                + " (expected " + entity.getCanonicalName() + "#field)"));
            }
        }
        return violations;
    }

    private static Set<String> fieldReferences(Class<?> entity) {
        Set<String> references = new HashSet<>();
        for (Class<?> current = entity; current != null && current != Object.class; current = current.getSuperclass()) {
            for (Field field : current.getDeclaredFields()) {
                if (!Modifier.isStatic(field.getModifiers())) {
                    references.add(entity.getCanonicalName() + "#" + field.getName());
                }
            }
        }
        return references;
    }
}
