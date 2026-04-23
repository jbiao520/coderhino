package com.coderhino.commands.builtin;

import com.coderhino.commands.CommandContext;
import com.coderhino.commands.CommandRegistry;
import com.coderhino.commands.PromptCommandExecutor;
import com.coderhino.cli.PrintStreamTerminalRenderer;
import com.coderhino.services.CostTracker;
import com.coderhino.services.ServiceRegistry;
import com.coderhino.services.lsp.LspClientManager;
import com.coderhino.services.mcp.McpConnectionManager;
import com.coderhino.services.tasks.TaskService;
import com.coderhino.state.AppState;
import com.coderhino.state.BootstrapState;
import com.coderhino.state.SessionRuntime;
import com.coderhino.state.SessionStore;
import com.coderhino.types.Message;
import com.coderhino.types.PermissionMode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClearCommandTest {

    @Test
    void clearStartsFreshSessionAndPreservesRuntimeConfiguration(@TempDir Path tempDir) {
        var originalSessionId = UUID.randomUUID();
        var state = new BootstrapState(new AppState(
            true,
            "claude-sonnet",
            tempDir.toAbsolutePath().normalize().toString(),
            true,
            true,
            PermissionMode.ACCEPT_EDITS,
            12.5,
            100L,
            50L,
            25L,
            10L,
            3,
            new AppState.CurrentUsage(7L, 8L, 9L, 10L, 2),
            populatedSessionRuntime(originalSessionId),
            List.of(new Message.UserMessage("hello"), new Message.AssistantMessage("world"))
        ));
        var outBuffer = new ByteArrayOutputStream();
        var costTracker = new CostTracker();
        costTracker.addUsage("claude-sonnet", 321, 123, 11, 22);
        var context = context(tempDir, state, outBuffer, costTracker);

        new ClearCommand().execute(context, "");

        var next = state.get();
        assertNotEquals(originalSessionId, next.sessionRuntime().sessionId());
        assertTrue(next.messages().isEmpty());
        assertTrue(next.sessionRuntime().transcript().isEmpty());
        assertTrue(next.sessionRuntime().rawAiHistory().isEmpty());
        assertNull(next.sessionRuntime().customTitle());

        assertEquals(0L, next.totalInputTokens());
        assertEquals(0L, next.totalOutputTokens());
        assertEquals(0L, next.totalCacheReadTokens());
        assertEquals(0L, next.totalCacheWriteTokens());
        assertEquals(0, next.totalToolUses());
        assertEquals(0.0, next.totalCostUsd());
        assertNull(next.currentUsage());

        assertTrue(next.verbose());
        assertEquals("claude-sonnet", next.model());
        assertEquals(tempDir.toAbsolutePath().normalize().toString(), next.cwd());
        assertTrue(next.interactive());
        assertTrue(next.running());
        assertEquals(PermissionMode.ACCEPT_EDITS, next.permissionMode());

        assertTrue(costTracker.allModelUsage().isEmpty());
        assertEquals("Conversation cleared.", outBuffer.toString().trim());
    }

    @Test
    void clearResetsUsageCommandOutput(@TempDir Path tempDir) {
        var state = new BootstrapState(new AppState(
            false,
            "claude-sonnet",
            tempDir.toAbsolutePath().normalize().toString(),
            false,
            true,
            PermissionMode.BYPASS,
            9.75,
            200L,
            150L,
            50L,
            25L,
            4,
            new AppState.CurrentUsage(3L, 4L, 5L, 6L, 1),
            populatedSessionRuntime(UUID.randomUUID()),
            List.of(new Message.UserMessage("before clear"))
        ));
        var clearOut = new ByteArrayOutputStream();
        var usageOut = new ByteArrayOutputStream();
        var costTracker = new CostTracker();
        costTracker.addUsage("claude-sonnet", 999, 888, 77, 66);
        var context = context(tempDir, state, clearOut, costTracker);

        new ClearCommand().execute(context, "");
        new UsageCommand().execute(contextWithOut(context, usageOut), "");

        var usageText = usageOut.toString();
        assertTrue(usageText.contains("Input tokens:        0"));
        assertTrue(usageText.contains("Output tokens:       0"));
        assertTrue(usageText.contains("Cache read tokens:   0"));
        assertTrue(usageText.contains("Cache write tokens:  0"));
        assertTrue(usageText.contains("Tool invocations:    0"));
        assertTrue(usageText.contains("Messages:            0"));
        assertTrue(usageText.contains("Transcript entries:  0"));
        assertTrue(usageText.contains("Total cost:          $0.000000"));
        assertFalse(usageText.contains("Per-Model Breakdown:"));
    }

    private static SessionRuntime populatedSessionRuntime(UUID sessionId) {
        var envelope = new Message.Envelope(
            UUID.randomUUID(),
            null,
            Instant.parse("2026-04-15T00:00:00Z"),
            new Message.AssistantMessage("transcript entry")
        );
        return new SessionRuntime(
            sessionId,
            envelope.uuid(),
            "Existing title",
            List.of(envelope),
            List.of(new SessionRuntime.RawAiHistoryEntry(
                Instant.parse("2026-04-15T00:00:01Z"),
                "response",
                "raw payload"
            )),
            List.of()
        );
    }

    private static CommandContext context(Path tempDir, BootstrapState bootstrapState, ByteArrayOutputStream outBuffer, CostTracker costTracker) {
        return new CommandContext(
            bootstrapState,
            new CommandRegistry(List.of()),
            new SessionStore(new ObjectMapper().registerModule(new JavaTimeModule()), tempDir.resolve("sessions")),
            new ServiceRegistry(new McpConnectionManager(), new LspClientManager(), new TaskService(tempDir.resolve("tasks.json")), costTracker),
            noPromptExecutor(),
            new PrintStreamTerminalRenderer(new PrintStream(outBuffer, true), new PrintStream(new ByteArrayOutputStream(), true)),
            new PrintStream(outBuffer, true),
            new PrintStream(new ByteArrayOutputStream(), true)
        );
    }

    private static CommandContext contextWithOut(CommandContext original, ByteArrayOutputStream outBuffer) {
        return new CommandContext(
            original.bootstrapState(),
            original.registry(),
            original.sessionStore(),
            original.services(),
            original.promptExecutor(),
            new PrintStreamTerminalRenderer(new PrintStream(outBuffer, true), original.err()),
            new PrintStream(outBuffer, true),
            original.err()
        );
    }

    private static PromptCommandExecutor noPromptExecutor() {
        return (context, definition, prompt) -> "";
    }
}
