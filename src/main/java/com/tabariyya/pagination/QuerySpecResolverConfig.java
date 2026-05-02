package com.tabariyya.pagination;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

@Configuration
public class QuerySpecResolverConfig implements WebMvcConfigurer {

    private final QuerySpecResolver querySpecResolver;

    public QuerySpecResolverConfig(QuerySpecResolver querySpecResolver) {
        this.querySpecResolver = querySpecResolver;
    }

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(querySpecResolver);
    }
}
