package com.tabariyya.pagination;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

@Configuration
public class QuerySpecResolverConfig implements WebMvcConfigurer {

    private final QuerySpecResolver querySpecResolver;
    private final AggregationRequestResolver aggregationRequestResolver;

    public QuerySpecResolverConfig(QuerySpecResolver querySpecResolver,
                                   AggregationRequestResolver aggregationRequestResolver) {
        this.querySpecResolver = querySpecResolver;
        this.aggregationRequestResolver = aggregationRequestResolver;
    }

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(querySpecResolver);
        resolvers.add(aggregationRequestResolver);
    }
}
