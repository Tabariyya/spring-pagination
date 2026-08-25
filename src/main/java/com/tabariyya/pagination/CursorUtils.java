package com.tabariyya.pagination;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.lang.reflect.Field;
import java.util.Base64;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

public final class CursorUtils {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    static {
        objectMapper.findAndRegisterModules();
    }

    private CursorUtils() {
    }

    /**
     * Builds the cursor for the next page from the last entity of the current page.
     * Returns null when lastEntity is null (empty page), meaning there is no next page.
     */
    public static String nextCursor(QuerySpec<?> querySpec, Object lastEntity) {
        if (lastEntity == null) {
            return null;
        }
        try {
            Map<String, Object> lastValues = new LinkedHashMap<>();
            JsonNode ordering = objectMapper.readTree(querySpec.getOrdering());
            Iterator<String> fieldNames = ordering.fieldNames();
            while (fieldNames.hasNext()) {
                String fieldName = fieldNames.next();
                Field field = FieldUtils.findField(querySpec.getEntityClass(), fieldName);
                field.setAccessible(true);
                lastValues.put(fieldName, field.get(lastEntity));
            }

            Cursor cursor = new Cursor(querySpec.getFilters(), querySpec.getOrdering(),
                    querySpec.getAggregations(), querySpec.getGroupBy(), querySpec.getLimit(), lastValues);
            byte[] json = objectMapper.writeValueAsBytes(cursor);
            return Base64.getUrlEncoder().withoutPadding().encodeToString(json);
        } catch (Throwable e) {
            throw new GenericQueryDslException(e);
        }
    }

    public static Cursor decode(String token) {
        try {
            byte[] json = Base64.getUrlDecoder().decode(token);
            return objectMapper.readValue(json, Cursor.class);
        } catch (Exception e) {
            throw new InvalidCursorException("Malformed cursor: " + token, e);
        }
    }
}
