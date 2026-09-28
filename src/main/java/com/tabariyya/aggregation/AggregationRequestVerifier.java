package com.tabariyya.aggregation;

import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.core.MethodParameter;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Fails startup when an endpoint takes an {@link AggregationRequest} but gives no way to tell which
 * fields it may aggregate: no {@link AggregateOver} on the parameter, and no response type to read
 * them from. Such an endpoint would otherwise start fine and reject every request it receives.
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
            for (MethodParameter parameter : handlerMethod.getMethodParameters()) {
                if (AggregationRequest.class.isAssignableFrom(parameter.getParameterType())
                        && !parameter.hasParameterAnnotation(AggregateOver.class)
                        && AggregationRequestResolver.responseFieldSource(parameter) == null) {
                    violations.add(handlerMethod.getBeanType().getSimpleName() + "#"
                            + handlerMethod.getMethod().getName()
                            + " needs @AggregateOver on its AggregationRequest parameter");
                }
            }
        }
        return violations;
    }
}
