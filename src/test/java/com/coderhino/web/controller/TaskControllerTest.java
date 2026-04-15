package com.coderhino.web.controller;

import com.coderhino.services.ServiceRegistry;
import com.coderhino.services.tasks.TaskService;
import com.coderhino.web.dto.TaskCompletionListDto;
import com.coderhino.web.notifications.CompletionNotificationStore;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class TaskControllerTest {

    @Test
    void listCompletionsReturnsDoneTasksWithOriginMetadata() {
        var tasks = new TaskService(null, null, Executors.newSingleThreadExecutor());
        var completionStore = new CompletionNotificationStore();
        try {
            tasks.submit("Background task", "proj-1", "ses-1", () -> "done");
            waitForCompletion(tasks);

            var controller = new TaskController(new ServiceRegistry(
                new com.coderhino.services.mcp.McpConnectionManager(),
                new com.coderhino.services.lsp.LspClientManager(),
                tasks
            ), completionStore);

            TaskCompletionListDto response = controller.listCompletions(0L);

            assertEquals(1, response.completions().size());
            var completion = response.completions().get(0);
            assertEquals("Background task", completion.description());
            assertEquals("proj-1", completion.projectId());
            assertEquals("ses-1", completion.sessionId());
            assertNotNull(completion.taskId());
            assertNotNull(completion.completedAt());
        } finally {
            tasks.shutdown();
        }
    }

    @Test
    void listCompletionsIncludesAiRunNotifications() {
        var tasks = new TaskService(null, null, Executors.newSingleThreadExecutor());
        var completionStore = new CompletionNotificationStore();
        completionStore.recordAiRunCompletion("run-1", "ses-1", "proj-1", Instant.parse("2026-04-12T00:00:00Z"));
        try {
            var controller = new TaskController(new ServiceRegistry(
                new com.coderhino.services.mcp.McpConnectionManager(),
                new com.coderhino.services.lsp.LspClientManager(),
                tasks
            ), completionStore);

            TaskCompletionListDto response = controller.listCompletions(0L);

            assertEquals(1, response.completions().size());
            var completion = response.completions().get(0);
            assertEquals("run-1", completion.completionId());
            assertEquals("run-1", completion.runId());
            assertEquals("AI run completed", completion.description());
            assertEquals("proj-1", completion.projectId());
            assertEquals("ses-1", completion.sessionId());
            assertNotNull(completion.completedAt());
        } finally {
            tasks.shutdown();
        }
    }

    private static void waitForCompletion(TaskService tasks) {
        for (int i = 0; i < 50; i++) {
            if (tasks.list().stream().allMatch(task -> "done".equals(task.status()))) {
                return;
            }
            try {
                Thread.sleep(20L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new AssertionError("Interrupted while waiting for task completion", e);
            }
        }
        throw new AssertionError("Task did not reach done status in time");
    }
}
