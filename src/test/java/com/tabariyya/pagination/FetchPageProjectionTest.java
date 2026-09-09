package com.tabariyya.pagination;

import com.querydsl.core.Tuple;
import com.querydsl.core.types.Expression;
import com.querydsl.core.types.MappingProjection;
import com.querydsl.core.types.dsl.PathBuilder;
import com.querydsl.jpa.impl.JPAQuery;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The count on the first page is a window function, so it shares the select list with whatever the
 * row is read as. These pin down that the caller's own projection is what it shares it with.
 */
class FetchPageProjectionTest {

    private final PathBuilder<TestActivity> root = new PathBuilder<>(TestActivity.class, "activity");

    @Test
    void projection_selectedByTheCaller_isKept() {
        MappingProjection<TestActivity> projection = new MappingProjection<>(TestActivity.class, root) {
            @Override
            protected TestActivity map(Tuple row) {
                return row.get(root);
            }
        };
        JPAQuery<TestActivity> query = new JPAQuery<TestActivity>().from(root).select(projection);

        Expression<?> resolved = QuerySpec.projectionOf(query);

        assertThat(resolved).isSameAs(projection);
    }

    @Test
    void projection_whenNoneWasSelected_isTheRoot() {
        JPAQuery<TestActivity> query = new JPAQuery<TestActivity>().from(root);

        Expression<?> resolved = QuerySpec.projectionOf(query);

        assertThat(resolved).isSameAs(root);
    }

    @SuppressWarnings("unused")
    private static class TestActivity {
        private UUID id;
        private String title;
    }
}
