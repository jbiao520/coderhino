package com.coderhino.types;

import java.util.Map;

public record ToolInputSchema(String type, Map<String, Object> properties) {
    public static ToolInputSchema object(Map<String, Object> properties) {
        return new ToolInputSchema("object", Map.copyOf(properties));
    }
}
