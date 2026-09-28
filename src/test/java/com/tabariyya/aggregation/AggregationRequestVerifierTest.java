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
    }

    private static class TestActivity {
        private int score;
    }

    private record TestResponse(int score) {}
}
