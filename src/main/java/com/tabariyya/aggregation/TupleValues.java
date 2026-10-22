package com.tabariyya.aggregation;

import com.tabariyya.pagination.GenericQueryDslException;
import com.querydsl.core.Tuple;
import com.querydsl.core.types.Expression;
import com.querydsl.core.util.MathUtils;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

final class TupleValues {

    private TupleValues() {
    }

    static <T> T read(Tuple tuple, Expression<T> expression, Class<T> type, String name) {
        Object value = tuple.get(expression);
        if (value == null || type.isInstance(value)) {
            return type.cast(value);
        }
        if (value instanceof Number number && Number.class.isAssignableFrom(type)) {
            return type.cast(MathUtils.cast(number, type.asSubclass(Number.class)));
        }
        if (type == LocalDate.class) {
            LocalDate day = toLocalDate(value);
            if (day != null) {
                return type.cast(day);
            }
        }
        throw new GenericQueryDslException(new ClassCastException("'" + name + "' returned a "
                + value.getClass().getSimpleName() + ", which is not a " + type.getSimpleName()));
    }

    private static LocalDate toLocalDate(Object value) {
        if (value instanceof java.sql.Date date) {
            return date.toLocalDate();
        }
        if (value instanceof Timestamp timestamp) {
            return timestamp.toLocalDateTime().toLocalDate();
        }
        if (value instanceof LocalDateTime dateTime) {
            return dateTime.toLocalDate();
        }
        if (value instanceof OffsetDateTime dateTime) {
            return dateTime.withOffsetSameInstant(ZoneOffset.UTC).toLocalDate();
        }
        if (value instanceof Instant instant) {
            return instant.atOffset(ZoneOffset.UTC).toLocalDate();
        }
        return null;
    }
}
