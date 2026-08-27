package com.tabariyya.pagination;

import com.querydsl.core.types.EntityPath;
import com.querydsl.core.types.dsl.PathBuilder;

public final class PathBuilders {

    private PathBuilders() {
    }

    public static PathBuilder<?> of(Class<?> entity) {
        try {
            String className = entity.getSimpleName();
            String packageName = entity.getPackage().getName();
            String qFqn = packageName + ".Q" + className;
            Class<?> qClass = Class.forName(qFqn);
            String instanceName = className.substring(0, 1).toLowerCase() + className.substring(1);
            EntityPath<?> entityPath = (EntityPath<?>) qClass.getField(instanceName).get(null);
            return new PathBuilder<>(entityPath.getType(), entityPath.getMetadata());
        } catch (Throwable e) {
            throw new GenericQueryDslException(e);
        }
    }
}
