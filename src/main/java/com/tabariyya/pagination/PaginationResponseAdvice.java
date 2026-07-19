package com.tabariyya.pagination;

import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

import java.util.List;

/**
 * Wraps the result list of every request that resolved a {@link QuerySpec} into
 * a {@link PaginatedResult} carrying the total count (first page only) and the
 * next-page cursor, so controllers and services just return the results.
 */
@ControllerAdvice
public class PaginationResponseAdvice implements ResponseBodyAdvice<Object> {

    @Override
    public boolean supports(MethodParameter returnType, Class<? extends HttpMessageConverter<?>> converterType) {
        return true;
    }

    @Override
    public Object beforeBodyWrite(Object body, MethodParameter returnType, MediaType selectedContentType,
                                  Class<? extends HttpMessageConverter<?>> selectedConverterType,
                                  ServerHttpRequest request, ServerHttpResponse response) {
        if (!(body instanceof List<?> results)) {
            return body;
        }

        RequestAttributes requestAttributes = RequestContextHolder.getRequestAttributes();
        if (requestAttributes == null) {
            return body;
        }

        Object attribute = requestAttributes.getAttribute(QuerySpec.class.getName(), RequestAttributes.SCOPE_REQUEST);
        if (!(attribute instanceof QuerySpec<?> querySpec)) {
            return body;
        }

        return new PaginatedResult<>(querySpec.getCount(), results, querySpec.getNextCursor());
    }
}
