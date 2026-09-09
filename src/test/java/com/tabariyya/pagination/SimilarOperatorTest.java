package com.tabariyya.pagination;

import com.querydsl.core.types.Predicate;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SimilarOperatorTest {

    private final QueryBuilderService queryBuilderService = new QueryBuilderService();

    @Test
    void similar_onTextField_buildsTrgmSimilarCall() {
        Predicate predicate = queryBuilderService.buildFilter(TestActivity.class, "{\"title\":{\"$similar\":\"kahve\"}}");

        assertThat(predicate.toString()).contains("trgm_similar").contains("title").contains("kahve");
    }

    @Test
    void similar_insideOr_buildsOneCallPerField() {
        Predicate predicate = queryBuilderService.buildFilter(
                TestActivity.class,
                "{\"$or\":[{\"title\":{\"$similar\":\"kahve\"}},{\"content\":{\"$similar\":\"kahve\"}}]}");

        String rendered = predicate.toString();
        assertThat(rendered).contains("title").contains("content").contains("||");
        assertThat(rendered.split("trgm_similar", -1)).hasSize(3);
    }

    @Test
    void similar_onNonTextField_isRejected() {
        assertThatThrownBy(
                        () -> queryBuilderService.buildFilter(TestActivity.class, "{\"capacity\":{\"$similar\":\"5\"}}"))
                .isInstanceOf(GenericQueryDslException.class)
                .hasRootCauseMessage("$similar is only supported on text fields, but 'capacity' is Integer");
    }

    @Test
    void similar_withNonStringTerm_isRejected() {
        assertThatThrownBy(() -> queryBuilderService.buildFilter(TestActivity.class, "{\"title\":{\"$similar\":5}}"))
                .isInstanceOf(GenericQueryDslException.class)
                .hasRootCauseMessage("$similar requires a string term for field: title");
    }

    @Test
    void similar_withBlankTerm_isRejected() {
        assertThatThrownBy(
                        () -> queryBuilderService.buildFilter(TestActivity.class, "{\"title\":{\"$similar\":\"   \"}}"))
                .isInstanceOf(GenericQueryDslException.class)
                .hasRootCauseMessage("$similar requires a non-blank term for field: title");
    }

    @SuppressWarnings("unused")
    private static class TestActivity {
        private UUID id;
        private String title;
        private String content;
        private Integer capacity;
    }
}
