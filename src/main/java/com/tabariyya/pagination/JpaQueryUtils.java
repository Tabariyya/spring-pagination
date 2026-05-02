package com.tabariyya.pagination;

import com.querydsl.core.types.EntityPath;
import com.querydsl.core.types.dsl.Wildcard;
import com.querydsl.jpa.impl.JPAQuery;
import com.querydsl.jpa.impl.JPAQueryFactory;

public class JpaQueryUtils {

    public static long getCount(JPAQuery<?> query, JPAQueryFactory jpaQueryFactory) {
        EntityPath<?> root = (EntityPath<?>) query.getMetadata().getJoins().get(0).getTarget();

        Long count = jpaQueryFactory
                .select(Wildcard.count)
                .from(root)
                .where(query.getMetadata().getWhere())
                .fetchOne();

        return count != null ? count : 0L;
    }
}
