package com.tabariyya.pagination;

import com.querydsl.core.BooleanBuilder;
import com.querydsl.core.types.ExpressionUtils;
import com.querydsl.core.types.Predicate;
import com.querydsl.core.types.dsl.CollectionPath;
import com.querydsl.core.types.dsl.PathBuilder;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CollectionFilterTest {

    private final QueryBuilderService queryBuilderService = new QueryBuilderService();

    private final CollectionPath<Gender, PathBuilder<Gender>> targetGenders =
            PathBuilders.of(TestActivity.class).getCollection("targetGenders", Gender.class);

    @Test
    void in_onCollectionField_matchesAnyListedElement() {
        Predicate predicate = filter("{\"targetGenders\":{\"$in\":[\"MALE\",\"FEMALE\"]}}");

        assertThat(predicate)
                .isEqualTo(ExpressionUtils.anyOf(
                        targetGenders.contains(Gender.MALE), targetGenders.contains(Gender.FEMALE)));
    }

    @Test
    void in_withOneElement_isAPlainContains() {
        Predicate predicate = filter("{\"targetGenders\":{\"$in\":[\"MALE\"]}}");

        assertThat(predicate).isEqualTo(targetGenders.contains(Gender.MALE));
    }

    @Test
    void nin_onCollectionField_matchesNoneOfTheListedElements() {
        Predicate predicate = filter("{\"targetGenders\":{\"$nin\":[\"MALE\",\"FEMALE\"]}}");

        assertThat(predicate)
                .isEqualTo(ExpressionUtils.anyOf(
                                targetGenders.contains(Gender.MALE), targetGenders.contains(Gender.FEMALE))
                        .not());
    }

    @Test
    void eq_onCollectionField_isAContains() {
        Predicate predicate = filter("{\"targetGenders\":{\"$eq\":\"MALE\"}}");

        assertThat(predicate).isEqualTo(targetGenders.contains(Gender.MALE));
    }

    @Test
    void bareValue_onCollectionField_isAContains() {
        Predicate predicate = filter("{\"targetGenders\":\"MALE\"}");

        assertThat(predicate).isEqualTo(targetGenders.contains(Gender.MALE));
    }

    @Test
    void ne_onCollectionField_isANegatedContains() {
        Predicate predicate = filter("{\"targetGenders\":{\"$ne\":\"MALE\"}}");

        assertThat(predicate).isEqualTo(targetGenders.contains(Gender.MALE).not());
    }

    @Test
    void not_onCollectionField_negatesTheInnerCondition() {
        Predicate predicate = filter("{\"targetGenders\":{\"$not\":{\"$in\":[\"MALE\"]}}}");

        assertThat(predicate).isEqualTo(targetGenders.contains(Gender.MALE).not());
    }

    @Test
    void in_withUnknownElement_isRejected() {
        assertThatThrownBy(() -> queryBuilderService.buildFilter(
                        TestActivity.class, "{\"targetGenders\":{\"$in\":[\"NOT_A_GENDER\"]}}"))
                .isInstanceOf(GenericQueryDslException.class)
                .hasCauseInstanceOf(InvalidFilterValueException.class);
    }

    @Test
    void in_withEmptyArray_isRejected() {
        assertThatThrownBy(() ->
                        queryBuilderService.buildFilter(TestActivity.class, "{\"targetGenders\":{\"$in\":[]}}"))
                .isInstanceOf(GenericQueryDslException.class)
                .hasRootCauseMessage("$in requires a non-empty array for field: targetGenders");
    }

    @Test
    void in_withoutAnArray_isRejected() {
        assertThatThrownBy(() ->
                        queryBuilderService.buildFilter(TestActivity.class, "{\"targetGenders\":{\"$in\":\"MALE\"}}"))
                .isInstanceOf(GenericQueryDslException.class)
                .hasRootCauseMessage("$in requires a non-empty array for field: targetGenders");
    }

    @Test
    void unsupportedOperator_onCollectionField_isRejected() {
        assertThatThrownBy(() ->
                        queryBuilderService.buildFilter(TestActivity.class, "{\"targetGenders\":{\"$gt\":\"MALE\"}}"))
                .isInstanceOf(GenericQueryDslException.class)
                .hasRootCauseMessage("$gt is not supported on collection field: targetGenders");
    }

    @Test
    void null_onCollectionField_isRejected() {
        assertThatThrownBy(() -> queryBuilderService.buildFilter(TestActivity.class, "{\"targetGenders\":null}"))
                .isInstanceOf(GenericQueryDslException.class)
                .hasRootCauseMessage("Collection field 'targetGenders' cannot be compared to null");
    }

    @Test
    void in_onScalarField_isStillAnInList() {
        Predicate predicate = filter("{\"capacity\":{\"$in\":[1,2]}}");

        assertThat(predicate.toString()).isEqualTo("testActivity.capacity in [1, 2]");
    }

    // buildFilter wraps every filter in a BooleanBuilder; a single condition is its only value.
    private Predicate filter(String json) {
        return ((BooleanBuilder) queryBuilderService.buildFilter(TestActivity.class, json)).getValue();
    }

    enum Gender {
        MALE,
        FEMALE
    }

    @SuppressWarnings("unused")
    private static class TestActivity {
        private UUID id;
        private Integer capacity;
        private Set<Gender> targetGenders = new HashSet<>();
    }
}
