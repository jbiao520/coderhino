package com.coderhino.query;

import java.util.Map;

public record ToolSchema(String name, String description, Map<String, Object> inputSchema) {
}
