package com.tabariyya.aggregation;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

@Configuration
public class AggregationResolverConfig implements WebMvcConfigurer {

    private final AggregationRequestResolver aggregationRequestResolver;

    public AggregationResolverConfig(AggregationRequestResolver aggregationRequestResolver) {
        this.aggregationRequestResolver = aggregationRequestResolver;
    }

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(aggregationRequestResolver);
    }
}
