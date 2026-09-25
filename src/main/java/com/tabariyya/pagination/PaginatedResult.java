package com.tabariyya.pagination;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.tabariyya.aggregation.AggregationGroup;

import java.util.List;

/**
 * Standard response body for cursor-paginated listings, built by
 * {@link PaginationResponseAdvice} from the result list a handler returns. The
 * count is null on cursor requests and the nextCursor is null when there is no
 * further page.
 */
public record PaginatedResult<T>(Long count, List<T> result, String nextCursor,
                                 @JsonInclude(JsonInclude.Include.NON_NULL) List<AggregationGroup> aggregations) {
}
