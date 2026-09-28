package com.tabariyya.aggregation;

import com.tabariyya.pagination.UnknownResponseFieldException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AggregationQueryBuilderTest {

    private static final String SCORE = "com.tabariyya.aggregation.AggregationQueryBuilderTest.TestActivity#score";
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

    private static class TestActivity {
        private int score;
    }
}
