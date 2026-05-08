package io.github.paulirwin.javaapiextractor;

import com.fasterxml.jackson.databind.ObjectMapper;

public class JsonSerializer {
    public static String serialize(Object obj) {
        var objectMapper = new ObjectMapper();

        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
