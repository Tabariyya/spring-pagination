package com.tabariyya.pagination;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/**
 * Standard response body for cursor-paginated listings, built by
 * {@link PaginationResponseAdvice} from the result list a handler returns. The
 * total count is only present on the first page and the nextCursor only when a
 * further page exists; both are omitted from the JSON when null.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record PaginatedResult<T>(Long count, List<T> result, String nextCursor) {
}
