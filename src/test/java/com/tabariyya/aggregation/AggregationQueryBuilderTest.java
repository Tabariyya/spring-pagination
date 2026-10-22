package com.tabariyya.aggregation;

import com.tabariyya.pagination.UnknownResponseFieldException;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AggregationQueryBuilderTest {

    private static final String SCORE = "com.tabariyya.aggregation.AggregationQueryBuilderTest.TestActivity#score";
    private static final String CREATED_AT = "com.tabariyya.aggregation.AggregationQueryBuilderTest.TestActivity#createdAt";
    private static final String GROUP_BY_SCORE = "{\"$groupBy\":\"$score\"}";

    private final AggregationQueryBuilder builder = new AggregationQueryBuilder();

    @Test
    void requestedField_matchesItsEntityReference() {
        assertThatCode(() -> builder.validateGroupByAgainstFields(TestActivity.class, new String[] {SCORE}, GROUP_BY_SCORE))
                .doesNotThrowAnyException();
    }

    @Test
    void requestedField_doesNotMatchAPlainName() {
        assertThatThrownBy(() ->
                        builder.validateGroupByAgainstFields(TestActivity.class, new String[] {"score"}, GROUP_BY_SCORE))
                .isInstanceOf(UnknownResponseFieldException.class);
    }

    @Test
    void dateTruncField_isValidatedByItsName() {
        assertThatCode(() -> builder.validateGroupByAgainstFields(
                        TestActivity.class, new String[] {CREATED_AT}, dateTrunc("createdAt", "day")))
                .doesNotThrowAnyException();
    }

    @Test
    void dateTrunc_groupsByADayKeyedByTheFieldName() {
        GroupBySpec spec = builder.buildGroupBy(TestActivity.class, dateTrunc("createdAt", "week"));

        assertThat(spec.expressions()[0].getType()).isEqualTo(LocalDate.class);
    }

    @Test
    void age_producesAnInteger() {
        GroupBySpec spec = builder.buildGroupBy(TestActivity.class, "{\"$groupBy\":[{\"$age\":\"$birthDate\"}]}");

        assertThat(spec.expressions()[0].getType()).isEqualTo(Integer.class);
    }

    @Test
    void plainAndOperatorFields_canBeMixed() {
        GroupBySpec spec = builder.buildGroupBy(TestActivity.class,
                "{\"$groupBy\":[\"$score\",{\"$dateTrunc\":{\"date\":\"$createdAt\",\"unit\":\"month\"}}]}");

        assertThat(spec.expressions()).hasSize(2);
    }

    @Test
    void unknownUnit_isRejected() {
        assertThatThrownBy(() -> builder.buildGroupBy(TestActivity.class, dateTrunc("createdAt", "hour")))
                .isInstanceOf(InvalidAggregationException.class)
                .hasMessageContaining("hour");
    }

    @Test
    void dateTruncWithoutUnit_isRejected() {
        assertThatThrownBy(() -> builder.buildGroupBy(TestActivity.class,
                        "{\"$groupBy\":[{\"$dateTrunc\":{\"date\":\"$createdAt\"}}]}"))
                .isInstanceOf(InvalidAggregationException.class)
                .hasMessageContaining("unit");
    }

    @Test
    void unknownOperator_isRejected() {
        assertThatThrownBy(() -> builder.buildGroupBy(TestActivity.class,
                        "{\"$groupBy\":[{\"$round\":\"$score\"}]}"))
                .isInstanceOf(InvalidAggregationException.class)
                .hasMessageContaining("$round");
    }

    @Test
    void dateTruncOnNonDateField_isRejected() {
        assertThatThrownBy(() -> builder.buildGroupBy(TestActivity.class, dateTrunc("score", "day")))
                .isInstanceOf(InvalidAggregationException.class)
                .hasMessageContaining("date field");
    }

    @Test
    void sameFieldTwice_isRejected() {
        assertThatThrownBy(() -> builder.buildGroupBy(TestActivity.class,
                        "{\"$groupBy\":[\"$createdAt\",{\"$dateTrunc\":{\"date\":\"$createdAt\",\"unit\":\"day\"}}]}"))
                .isInstanceOf(InvalidAggregationException.class)
                .hasMessageContaining("more than once");
    }

    private static String dateTrunc(String field, String unit) {
        return "{\"$groupBy\":[{\"$dateTrunc\":{\"date\":\"$" + field + "\",\"unit\":\"" + unit + "\"}}]}";
    }

    private static class TestActivity {
        private int score;
        private Instant createdAt;
        private LocalDate birthDate;
    }
}
