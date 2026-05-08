package io.github.paulirwin.javaapiextractor;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

public class JsonSerializer {
    private static final ObjectMapper OBJECT_MAPPER = JsonMapper.builder().build();

    public static String serialize(Object obj) {
        try {
            return OBJECT_MAPPER.writeValueAsString(obj);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
