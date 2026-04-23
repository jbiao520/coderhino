package com.coderhino.commands.builtin;

import com.coderhino.commands.CommandContext;
import com.coderhino.commands.CommandRegistry;
import com.coderhino.commands.PromptCommandExecutor;
import com.coderhino.cli.PrintStreamTerminalRenderer;
import com.coderhino.services.ServiceRegistry;
import com.coderhino.services.compact.CompactService;
import com.coderhino.services.lsp.LspClientManager;
import com.coderhino.services.mcp.McpConnectionManager;
import com.coderhino.services.tasks.TaskService;
import com.coderhino.state.AppState;
import com.coderhino.state.BootstrapState;
import com.coderhino.state.SessionRuntime;
import com.coderhino.state.SessionStore;
import com.coderhino.types.CompactBoundary;
import com.coderhino.types.Message;
import com.coderhino.types.PermissionMode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CompactCommandTest {

    @Test
    void compactRewritesTranscriptWithSummaryFirstAndInstructionGuidance(@TempDir Path tempDir) {
        var state = bootstrapState(tempDir, List.of(
            new Message.UserMessage("First request"),
            new Message.AssistantMessage("First answer"),
            new Message.SystemMessage("[Earlier conversation compacted - 2 messages summarized]"),
            new Message.UserMessage("Recent request one"),
            new Message.AssistantMessage("Recent answer one"),
            new Message.UserMessage("Recent request two"),
            new Message.AssistantMessage("Recent answer two")
        ));
        var outBuffer = new ByteArrayOutputStream();
        var context = context(tempDir, state, outBuffer, new CompactService(new CompactBoundary(1, 1, 2)));

        new CompactCommand().execute(context, "focus on decisions");

        var nextState = state.get();
        assertEquals(3, nextState.messages().size());
        assertInstanceOf(Message.AssistantMessage.class, nextState.messages().get(0));
        assertTrue(nextState.messages().get(0).content().contains("Focus: focus on decisions"));
        assertFalse(nextState.messages().get(0).content().contains("[Compact Instructions]"));
        assertEquals("Recent request two", nextState.messages().get(1).content());
        assertEquals("Recent answer two", nextState.messages().get(2).content());
        assertEquals(3, nextState.sessionRuntime().transcript().size());
        assertTrue(outBuffer.toString().contains("Compaction complete."));
    }

    @Test
    void compactNoOpsWhenActiveScopeIsEmpty(@TempDir Path tempDir) {
        List<Message> messages = List.of(new Message.SystemMessage("[Earlier conversation compacted - 2 messages summarized]"));
        var state = bootstrapState(tempDir, messages);
        var outBuffer = new ByteArrayOutputStream();
        var context = context(tempDir, state, outBuffer, new CompactService(new CompactBoundary(1, 1, 2)));

        new CompactCommand().execute(context, "");

        assertEquals(messages, state.get().messages());
        assertTrue(outBuffer.toString().contains("No conversation to compact."));
    }

    @Test
    void compactLeavesStateUnchangedWhenNotEnoughHistory(@TempDir Path tempDir) {
        List<Message> messages = List.of(
            new Message.UserMessage("Latest request"),
            new Message.AssistantMessage("Latest answer")
        );
        var state = bootstrapState(tempDir, messages);
        var outBuffer = new ByteArrayOutputStream();
        var context = context(tempDir, state, outBuffer, new CompactService(new CompactBoundary(1, 1, 2)));

        new CompactCommand().execute(context, null);

        assertEquals(messages, state.get().messages());
        assertTrue(outBuffer.toString().contains("Not enough conversation history to compact yet."));
    }

    private static CommandContext context(Path tempDir, BootstrapState bootstrapState, ByteArrayOutputStream outBuffer, CompactService compactService) {
        return new CommandContext(
            bootstrapState,
            new CommandRegistry(List.of()),
            new SessionStore(new ObjectMapper().registerModule(new JavaTimeModule()), tempDir.resolve("sessions")),
            new ServiceRegistry(new McpConnectionManager(), new LspClientManager(), new TaskService(tempDir.resolve("tasks.json")), new com.coderhino.services.CostTracker(), compactService),
            noPromptExecutor(),
            new PrintStreamTerminalRenderer(new PrintStream(outBuffer, true), new PrintStream(new ByteArrayOutputStream(), true)),
            new PrintStream(outBuffer, true),
            new PrintStream(new ByteArrayOutputStream(), true)
        );
    }

    private static BootstrapState bootstrapState(Path cwd, List<Message> messages) {
        return new BootstrapState(new AppState(
            false,
            "MiniMax-M2.5",
            cwd.toAbsolutePath().normalize().toString(),
            false,
            true,
            PermissionMode.BYPASS,
            0.0,
            new SessionRuntime(UUID.randomUUID(), null, null, List.of(), List.of(), List.of()),
            messages
        ));
    }

    private static PromptCommandExecutor noPromptExecutor() {
        return (context, definition, prompt) -> "";
    }
}
