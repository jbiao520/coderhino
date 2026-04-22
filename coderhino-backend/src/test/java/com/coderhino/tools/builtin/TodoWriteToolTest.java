package com.coderhino.tools.builtin;

import com.coderhino.types.PermissionResult;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class TodoWriteToolTest {

    private final TodoWriteTool tool = new TodoWriteTool();

    @AfterEach
    void tearDown() {
        TodoWriteTool.clearTodos();
    }

    @Test
    void executeReplacesTodoListWithStructuredItems() {
        var input = new TodoWriteTool.Input(List.of(
            new TodoWriteTool.TodoItem("Investigate error", "in_progress", "high"),
            new TodoWriteTool.TodoItem("Add regression tests", "pending", "medium")
        ));

        var output = tool.execute(input, null);

        assertEquals(2, output.count());
        assertEquals(input.todos(), output.todos());
        assertEquals(input.todos(), TodoWriteTool.getTodos());
    }

    @Test
    void validateRejectsMissingStructuredFields() {
        var validation = tool.validate(
            new TodoWriteTool.Input(List.of(new TodoWriteTool.TodoItem("Investigate error", "", "high"))),
            null
        );

        var deny = assertInstanceOf(PermissionResult.Deny.class, validation);
        assertEquals("todo item 0 status must not be blank.", deny.reason());
    }

    @Test
    void inputSchemaExposesStructuredTodoItems() {
        var schema = tool.inputSchema();

        assertEquals("object", schema.type());
        @SuppressWarnings("unchecked")
        var todos = (Map<String, Object>) schema.properties().get("todos");
        assertEquals("array", todos.get("type"));

        @SuppressWarnings("unchecked")
        var items = (Map<String, Object>) todos.get("items");
        assertEquals("object", items.get("type"));

        @SuppressWarnings("unchecked")
        var properties = (Map<String, Object>) items.get("properties");
        assertEquals(List.of("content", "status", "priority"), items.get("required"));
        assertEquals(Map.of("type", "string", "description", "Todo description"), properties.get("content"));
        assertEquals(Map.of("type", "string", "description", "Todo status"), properties.get("status"));
        assertEquals(Map.of("type", "string", "description", "Todo priority"), properties.get("priority"));
    }
}
