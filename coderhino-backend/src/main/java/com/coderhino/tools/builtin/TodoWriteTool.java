package com.coderhino.tools.builtin;

import com.coderhino.tools.ToolContext;
import com.coderhino.tools.ToolDefinition;
import com.coderhino.types.PermissionResult;
import com.coderhino.types.ToolInputSchema;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

public final class TodoWriteTool implements ToolDefinition<TodoWriteTool.Input, TodoWriteTool.Output> {

    private static final CopyOnWriteArrayList<TodoItem> TODO_LIST = new CopyOnWriteArrayList<>();

    @Override
    public String name() {
        return "todo_write";
    }

    @Override
    public String description() {
        return "Replace the entire todo list with the provided list of items";
    }

    @Override
    public boolean isReadOnly() {
        return false;
    }

    @Override
    public ToolInputSchema inputSchema() {
        return ToolInputSchema.object(Map.of(
            "todos", Map.of(
                "type", "array",
                "description", "The full list of todo items to write",
                "items", Map.of(
                    "type", "object",
                    "properties", Map.of(
                        "content", Map.of("type", "string", "description", "Todo description"),
                        "status", Map.of("type", "string", "description", "Todo status"),
                        "priority", Map.of("type", "string", "description", "Todo priority")
                    ),
                    "required", List.of("content", "status", "priority")
                )
            )
        ));
    }

    @Override
    public PermissionResult validate(Input input, ToolContext context) {
        if (input == null) {
            return PermissionResult.deny("Input must not be null.");
        }
        if (input.todos() == null) {
            return PermissionResult.deny("todos must not be null.");
        }
        for (int index = 0; index < input.todos().size(); index++) {
            var todo = input.todos().get(index);
            if (todo == null) {
                return PermissionResult.deny("todo item %d must not be null.".formatted(index));
            }
            if (todo.content() == null || todo.content().isBlank()) {
                return PermissionResult.deny("todo item %d content must not be blank.".formatted(index));
            }
            if (todo.status() == null || todo.status().isBlank()) {
                return PermissionResult.deny("todo item %d status must not be blank.".formatted(index));
            }
            if (todo.priority() == null || todo.priority().isBlank()) {
                return PermissionResult.deny("todo item %d priority must not be blank.".formatted(index));
            }
        }
        return PermissionResult.allow();
    }

    @Override
    public Output execute(Input input, ToolContext context) {
        TODO_LIST.clear();
        TODO_LIST.addAll(input.todos());
        return new Output(Collections.unmodifiableList(new ArrayList<>(TODO_LIST)), TODO_LIST.size());
    }

    public static List<TodoItem> getTodos() {
        return Collections.unmodifiableList(new ArrayList<>(TODO_LIST));
    }

    public static void clearTodos() {
        TODO_LIST.clear();
    }

    public record Input(List<TodoItem> todos) {
    }

    public record Output(List<TodoItem> todos, int count) {
    }

    public record TodoItem(String content, String status, String priority) {
        public Map<String, String> toMap() {
            var map = new LinkedHashMap<String, String>();
            map.put("content", content);
            map.put("status", status);
            map.put("priority", priority);
            return Collections.unmodifiableMap(map);
        }
    }
}
