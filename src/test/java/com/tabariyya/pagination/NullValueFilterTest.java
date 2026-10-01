package com.tabariyya.pagination;

import com.querydsl.core.types.Predicate;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NullValueFilterTest {

    private final QueryBuilderService queryBuilderService = new QueryBuilderService();

    @Test
    void bareNull_buildsIsNull() {
        Predicate predicate = queryBuilderService.buildFilter(TestActivity.class, "{\"deletedAt\":null}");

        assertThat(predicate.toString()).endsWith("deletedAt is null");
    }

    @Test
    void eqNull_buildsIsNull() {
        Predicate predicate = queryBuilderService.buildFilter(TestActivity.class, "{\"deletedAt\":{\"$eq\":null}}");

        assertThat(predicate.toString()).endsWith("deletedAt is null");
    }

    @Test
    void neNull_buildsIsNotNull() {
        Predicate predicate = queryBuilderService.buildFilter(TestActivity.class, "{\"deletedAt\":{\"$ne\":null}}");

        assertThat(predicate.toString()).endsWith("deletedAt is not null");
    }

    @Test
    void notEqNull_buildsNegatedIsNull() {
        Predicate predicate =
                queryBuilderService.buildFilter(TestActivity.class, "{\"deletedAt\":{\"$not\":{\"$eq\":null}}}");

        assertThat(predicate.toString()).contains("deletedAt is null").startsWith("!");
    }

    @Test
    void nullInsideOr_combinesWithOtherConditions() {
        Predicate predicate = queryBuilderService.buildFilter(
                TestActivity.class,
                "{\"$or\":[{\"deletedAt\":null},{\"title\":{\"$contains\":\"kahve\"}}]}");

        assertThat(predicate.toString()).contains("deletedAt is null").contains("||").contains("kahve");
    }

    @Test
    void comparisonWithNull_isRejected() {
        assertThatThrownBy(
                        () -> queryBuilderService.buildFilter(TestActivity.class, "{\"deletedAt\":{\"$gt\":null}}"))
                .isInstanceOf(GenericQueryDslException.class)
                .hasRootCauseMessage("$gt does not accept null for field: deletedAt");
    }

    @Test
    void dateComparison_isUnchanged() {
        Predicate predicate = queryBuilderService.buildFilter(
                TestActivity.class, "{\"deletedAt\":{\"$gt\":\"1970-01-01T00:00:00.000Z\"}}");

        assertThat(predicate.toString()).contains("deletedAt > 1970-01-01T00:00:00Z");
    }

    @SuppressWarnings("unused")
    private static class TestActivity {
        private UUID id;
        private String title;
        private Instant deletedAt;
    }
}
