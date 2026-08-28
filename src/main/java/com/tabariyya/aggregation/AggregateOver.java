package com.tabariyya.aggregation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.PARAMETER)
public @interface AggregateOver {

    String[] groupBy() default {};

    String[] aggregate() default {};

    String[] filter() default {};
}
