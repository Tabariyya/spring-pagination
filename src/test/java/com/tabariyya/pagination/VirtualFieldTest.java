package com.tabariyya.pagination;

import com.querydsl.core.BooleanBuilder;
import com.querydsl.core.types.ConstantImpl;
import com.querydsl.core.types.Expression;
import com.querydsl.core.types.ExpressionUtils;
import com.querydsl.core.types.Ops;
import com.querydsl.core.types.Order;
import com.querydsl.core.types.OrderSpecifier;
import com.querydsl.core.types.Predicate;
import com.querydsl.core.types.dsl.Expressions;
import com.querydsl.core.types.dsl.PathBuilder;
import com.querydsl.jpa.JPAExpressions;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * A virtual field is a name a client may filter and order by that is not a field of the entity: the
 * service registers the expression it stands for, and the library uses that expression wherever it
 * would otherwise have built a path. These pin down that every place a name is turned into SQL - the
 * filter, the ordering, the keyset and the field check - goes through the registration.
 */
class VirtualFieldTest {

    private static final PathBuilder<TestJoiningRequest> REQUEST = PathBuilders.of(TestJoiningRequest.class);
    private static final PathBuilder<TestActivity> ACTIVITY = new PathBuilder<>(TestActivity.class, "activity");

    // Whether the request's activity has ended: a correlated subquery, so it adds no rows.
    private static final Expression<Boolean> ACTIVITY_ENDED = JPAExpressions.select(ACTIVITY.getBoolean("ended"))
            .from(ACTIVITY)
            .where(ACTIVITY.get("id").eq(REQUEST.get("activityId")));

    private final QueryBuilderService queryBuilderService = new QueryBuilderService(List.of(
            VirtualFields.of(TestJoiningRequest.class).field("activity.ended", Boolean.class, ACTIVITY_ENDED)));

    @Test
    void filter_bareValueOnVirtualField_comparesItsExpression() {
        assertThat(filter("{\"activity.ended\":false}"))
                .isEqualTo(Expressions.predicate(Ops.EQ, ACTIVITY_ENDED, ConstantImpl.create(false)));
    }

    @Test
    void filter_operatorOnVirtualField_comparesItsExpression() {
        assertThat(filter("{\"activity.ended\":{\"$ne\":true}}"))
                .isEqualTo(Expressions.predicate(Ops.NE, ACTIVITY_ENDED, ConstantImpl.create(true)));
    }

    @Test
    void filter_nullOnVirtualField_isANullCheckOnItsExpression() {
        assertThat(filter("{\"activity.ended\":{\"$ne\":null}}"))
                .isEqualTo(Expressions.predicate(Ops.IS_NOT_NULL, ACTIVITY_ENDED));
    }

    @Test
    void filter_virtualFieldAlongsideARealOne_combinesBoth() {
        UUID activityId = UUID.randomUUID();

        Predicate predicate = queryBuilderService.buildFilter(
                TestJoiningRequest.class,
                "{\"activity.ended\":true,\"activityId\":\"" + activityId + "\"}");

        assertThat(((BooleanBuilder) predicate).getValue())
                .isEqualTo(ExpressionUtils.and(
                        Expressions.predicate(Ops.EQ, ACTIVITY_ENDED, ConstantImpl.create(true)),
                        Expressions.predicate(Ops.EQ, REQUEST.get("activityId"), ConstantImpl.create(activityId))));
    }

    @Test
    void filter_virtualFieldOfAnotherEntity_isNotKnownHere() {
        assertThatThrownBy(() -> queryBuilderService.buildFilter(TestActivity.class, "{\"activity.ended\":true}"))
                .isInstanceOf(GenericQueryDslException.class)
                .hasRootCauseInstanceOf(NoSuchFieldException.class);
    }

    @Test
    void ordering_byVirtualField_ordersByItsExpression() {
        OrderSpecifier<?>[] ordering = queryBuilderService.buildOrderSpecifier(
                TestJoiningRequest.class,
                queryBuilderService.ensureIdTieBreaker("{\"activity.ended\":1,\"createdAt\":-1}"));

        assertThat(ordering)
                .containsExactly(
                        new OrderSpecifier(Order.ASC, ACTIVITY_ENDED),
                        new OrderSpecifier(Order.DESC, REQUEST.get("createdAt")),
                        new OrderSpecifier(Order.ASC, REQUEST.get("id")));
    }

    @Test
    void keyset_afterARowOrderedByVirtualField_seeksPastItsExpression() {
        UUID lastId = UUID.randomUUID();
        Map<String, Object> lastValues = new LinkedHashMap<>();
        lastValues.put("activity.ended", true);
        lastValues.put("id", lastId.toString());

        Predicate keyset = queryBuilderService.buildKeysetPredicate(
                TestJoiningRequest.class, "{\"activity.ended\":1,\"id\":1}", lastValues);

        assertThat(keyset)
                .hasToString(ExpressionUtils.or(
                                Expressions.predicate(Ops.GT, ACTIVITY_ENDED, ConstantImpl.create(true)),
                        ExpressionUtils.and(
                                Expressions.predicate(Ops.EQ, ACTIVITY_ENDED, ConstantImpl.create(true)),
                                Expressions.predicate(Ops.GT, REQUEST.get("id"), ConstantImpl.create(lastId))))
                        .toString());
    }

    @Test
    void keyset_convertsTheCursorValueToTheRegisteredType() {
        UUID lastId = UUID.randomUUID();
        Map<String, Object> lastValues = new LinkedHashMap<>();
        lastValues.put("activity.ended", "false");
        lastValues.put("id", lastId.toString());

        Predicate keyset = queryBuilderService.buildKeysetPredicate(
                TestJoiningRequest.class, "{\"activity.ended\":-1,\"id\":1}", lastValues);

        // Descending, so "after" is the smaller value, and the string from the cursor is a Boolean again.
        assertThat(keyset)
                .hasToString(ExpressionUtils.or(
                                Expressions.predicate(Ops.LT, ACTIVITY_ENDED, ConstantImpl.create(Boolean.FALSE)),
                        ExpressionUtils.and(
                                Expressions.predicate(Ops.EQ, ACTIVITY_ENDED, ConstantImpl.create(Boolean.FALSE)),
                                Expressions.predicate(Ops.GT, REQUEST.get("id"), ConstantImpl.create(lastId))))
                        .toString());
    }

    @Test
    void sortExpressions_areTheVirtualFieldsTheOrderingNames() {
        Map<String, Expression<?>> expressions = queryBuilderService.virtualSortExpressions(
                TestJoiningRequest.class, "{\"activity.ended\":1,\"createdAt\":1,\"id\":1}");

        assertThat(expressions).containsExactly(Map.entry("activity.ended", ACTIVITY_ENDED));
    }

    @Test
    void sortExpressions_withoutAVirtualField_areNone() {
        assertThat(queryBuilderService.virtualSortExpressions(TestJoiningRequest.class, "{\"createdAt\":1,\"id\":1}"))
                .isEmpty();
    }

    @Test
    void validation_acceptsAVirtualFieldTheResponseDoesNotDeclare() {
        assertThatCode(() -> queryBuilderService.validateFieldsAgainstResponse(
                        TestJoiningRequestResponse.class,
                        TestJoiningRequest.class,
                        "{\"activity.ended\":false}",
                        "{\"activity.ended\":1,\"createdAt\":1}"))
                .doesNotThrowAnyException();
    }

    @Test
    void validation_stillRejectsAnUnknownField() {
        assertThatThrownBy(() -> queryBuilderService.validateFieldsAgainstResponse(
                        TestJoiningRequestResponse.class, TestJoiningRequest.class, null, "{\"activity.title\":1}"))
                .isInstanceOf(UnknownResponseFieldException.class);
    }

    @Test
    void registration_ofANameTheEntityAlreadyHas_isRejected() {
        assertThatThrownBy(() -> VirtualFields.of(TestJoiningRequest.class)
                        .field("activityId", UUID.class, REQUEST.get("activityId", UUID.class)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("activityId");
    }

    @Test
    void registration_ofTheSameNameTwice_isRejected() {
        VirtualFields fields =
                VirtualFields.of(TestJoiningRequest.class).field("activity.ended", Boolean.class, ACTIVITY_ENDED);

        assertThatThrownBy(() -> fields.field("activity.ended", Boolean.class, ACTIVITY_ENDED))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("activity.ended");
    }

    @Test
    void cursor_readsAVirtualFieldFromTheValuesTheLastPageSelected() throws Exception {
        UUID lastId = UUID.randomUUID();
        TestJoiningRequest last = new TestJoiningRequest();
        last.id = lastId;
        QuerySpec<TestJoiningRequest> querySpec = pagedBy("{\"activity.ended\":1,\"id\":1}");
        querySpec.setLastVirtualValues(Map.of("activity.ended", true));

        Cursor cursor = CursorUtils.decode(CursorUtils.nextCursor(querySpec, last));

        assertThat(cursor.getLastValues()).containsEntry("activity.ended", true).containsEntry("id", lastId.toString());
    }

    @Test
    void cursor_whenAVirtualFieldCameBackNull_refusesRatherThanSkipRows() {
        TestJoiningRequest last = new TestJoiningRequest();
        last.id = UUID.randomUUID();
        QuerySpec<TestJoiningRequest> querySpec = pagedBy("{\"activity.ended\":1,\"id\":1}");
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("activity.ended", null);
        querySpec.setLastVirtualValues(values);

        assertThatThrownBy(() -> CursorUtils.nextCursor(querySpec, last))
                .isInstanceOf(GenericQueryDslException.class)
                .hasRootCauseMessage("Virtual field 'activity.ended' was null on the last row of the page; "
                        + "its expression must never be null (wrap it in coalesce), or paging would skip rows");
    }

    private QuerySpec<TestJoiningRequest> pagedBy(String ordering) {
        QuerySpec<TestJoiningRequest> querySpec = new QuerySpec<>();
        querySpec.setEntityClass(TestJoiningRequest.class);
        querySpec.setOrdering(ordering);
        querySpec.setLimit(1);
        return querySpec;
    }

    // buildFilter wraps every filter in a BooleanBuilder; a single condition is its only value.
    private Predicate filter(String json) {
        return ((BooleanBuilder) queryBuilderService.buildFilter(TestJoiningRequest.class, json)).getValue();
    }

    @SuppressWarnings("unused")
    private static class TestActivity {
        private UUID id;
        private boolean ended;
    }

    @SuppressWarnings("unused")
    private static class TestJoiningRequest {
        private UUID id;
        private UUID activityId;
        private Instant createdAt;
    }

    @SuppressWarnings("unused")
    private static class TestJoiningRequestResponse {
        private UUID id;
        private UUID activityId;
        private Instant createdAt;
    }
}
