package com.coderhino.tools.builtin;

import com.coderhino.tools.ToolContext;
import com.coderhino.tools.ToolDefinition;
import com.coderhino.types.PermissionResult;
import com.coderhino.types.ToolInputSchema;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class ConfigTool implements ToolDefinition<ConfigTool.Input, ConfigTool.Output> {

    private static final ConcurrentHashMap<String, String> STORE = new ConcurrentHashMap<>();

    @Override
    public String name() {
        return "config";
    }

    @Override
    public String description() {
        return "Read or write a configuration key-value pair";
    }

    @Override
    public ToolInputSchema inputSchema() {
        return ToolInputSchema.object(Map.of(
            "key", Map.of("type", "string"),
            "value", Map.of("type", "string")
        ));
    }

    @Override
    public boolean isReadOnly() {
        return false;
    }

    @Override
    public PermissionResult validate(Input input, ToolContext context) {
        if (input.key() == null || input.key().isBlank()) {
            return PermissionResult.deny("key must not be blank.");
        }
        return PermissionResult.allow();
    }

    @Override
    public Output execute(Input input, ToolContext context) {
        if (input.value() != null && !input.value().isBlank()) {
            STORE.put(input.key(), input.value());
            return new Output(input.key(), input.value(), "set");
        }
        String existing = STORE.get(input.key());
        if (existing != null) {
            return new Output(input.key(), existing, "read");
        }
        return new Output(input.key(), null, "not_found");
    }

    public static void clearStore() {
        STORE.clear();
    }

    public record Input(String key, String value) {
        public Input {
            if (key != null) key = key.strip();
            if (value != null) value = value.strip();
        }
    }

    public record Output(String key, String value, String operation) {
    }
}
