package com.tabariyya.pagination;

import com.querydsl.core.types.EntityPath;
import com.querydsl.core.types.dsl.PathBuilder;

public final class EntityPaths {

    private EntityPaths() {
    }

    public static PathBuilder<?> of(Class<?> entity) {
        try {
            String entityName = entity.getSimpleName();
            String instanceName = entityName.substring(0, 1).toLowerCase() + entityName.substring(1);
            Class<?> qClass = Class.forName(entity.getPackage().getName() + ".Q" + entityName);
            EntityPath<?> entityPath = (EntityPath<?>) qClass.getField(instanceName).get(null);
            return new PathBuilder<>(entityPath.getType(), entityPath.getMetadata());
        } catch (Throwable e) {
            throw new GenericQueryDslException(e);
        }
    }
}
