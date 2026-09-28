package com.tabariyya.aggregation;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link AggregateOver} takes plain field names as well as dto-generator's {@code "com.acme.User#id"}
 * constants; both have to reach validation as the bare field name.
 */
class AggregationRequestResolverTest {

    @Test
    void fieldNames_dropTheClassPartOfAConstant() {
        assertThat(AggregationRequestResolver.fieldNames(new String[] {"com.acme.User#id", "score"}))
                .containsExactly("id", "score");
    }
}
