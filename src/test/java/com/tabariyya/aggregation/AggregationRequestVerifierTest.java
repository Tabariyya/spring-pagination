package com.tabariyya.aggregation;

import org.junit.jupiter.api.Test;
import org.springframework.web.method.HandlerMethod;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * An endpoint taking an AggregationRequest has to say which fields it allows, either through
 * {@link AggregateOver} or through the element type it returns; these pin down which ones the verifier
 * turns away at startup.
 */
class AggregationRequestVerifierTest {

    private static final String SCORE = "com.tabariyya.aggregation.AggregationRequestVerifierTest.TestActivity#score";
    private static final String OTHER_SCORE = "com.tabariyya.aggregation.AggregationRequestVerifierTest.OtherEntity#score";

    @Test
    void endpoint_withAggregateOver_passes() throws NoSuchMethodException {
        assertThat(violationsOf("annotated")).isEmpty();
    }

    @Test
    void endpoint_returningGroupsWithoutAggregateOver_isReported() throws NoSuchMethodException {
        assertThat(violationsOf("unannotated"))
                .containsExactly("TestController#unannotated needs @AggregateOver on its AggregationRequest parameter");
    }

    @Test
    void endpoint_returningResponsesWithoutAggregateOver_passes() throws NoSuchMethodException {
        assertThat(violationsOf("unannotatedWithResponses")).isEmpty();
    }

    @Test
    void endpoint_namingItsEntitysConstant_passes() throws NoSuchMethodException {
        assertThat(violationsOf("withConstant")).isEmpty();
    }

    @Test
    void endpoint_namingAnotherEntitysConstant_isReported() throws NoSuchMethodException {
        assertThat(violationsOf("withOtherEntitysConstant"))
                .containsExactly("TestController#withOtherEntitysConstant names " + OTHER_SCORE
                        + ", which is not a field of TestActivity");
    }

    @Test
    void endpoint_namingAFieldTheEntityLacks_isReported() throws NoSuchMethodException {
        assertThat(violationsOf("withUnknownField"))
                .containsExactly("TestController#withUnknownField names rank, which is not a field of TestActivity");
    }

    private static List<String> violationsOf(String methodName) throws NoSuchMethodException {
        HandlerMethod handlerMethod = new HandlerMethod(
                new TestController(), TestController.class.getMethod(methodName, AggregationRequest.class));
        return AggregationRequestVerifier.violations(List.of(handlerMethod));
    }

    public static class TestController {

        public List<AggregationGroup> annotated(
                @AggregateOver(groupBy = "score") AggregationRequest<TestActivity> aggregationRequest) {
            return List.of();
        }

        public List<AggregationGroup> unannotated(AggregationRequest<TestActivity> aggregationRequest) {
            return List.of();
        }

        public List<TestResponse> unannotatedWithResponses(AggregationRequest<TestActivity> aggregationRequest) {
            return List.of();
        }

        public List<AggregationGroup> withConstant(
                @AggregateOver(groupBy = SCORE) AggregationRequest<TestActivity> aggregationRequest) {
            return List.of();
        }

        public List<AggregationGroup> withOtherEntitysConstant(
                @AggregateOver(groupBy = OTHER_SCORE) AggregationRequest<TestActivity> aggregationRequest) {
            return List.of();
        }

        public List<AggregationGroup> withUnknownField(
                @AggregateOver(groupBy = "rank") AggregationRequest<TestActivity> aggregationRequest) {
            return List.of();
        }
    }

    private static class TestActivity {
        private int score;
    }

    private static class OtherEntity {
        private int score;
    }

    private record TestResponse(int score) {}
}
