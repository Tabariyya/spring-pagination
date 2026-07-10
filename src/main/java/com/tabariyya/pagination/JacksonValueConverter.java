package com.tabariyya.pagination;

import com.fasterxml.jackson.databind.ObjectMapper;

public class JacksonValueConverter {
    private final ObjectMapper objectMapper;

    public JacksonValueConverter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        objectMapper.findAndRegisterModules();
    }

    public<T> T convertValue(Object value, Class<T> valueType) {
        try {
            return objectMapper.convertValue(value, valueType);
        } catch (IllegalArgumentException ex) {
            throw new InvalidFilterValueException(
                    "Cannot convert value to " + valueType.getSimpleName() + " : " + value,ex);
        }
    }
}
