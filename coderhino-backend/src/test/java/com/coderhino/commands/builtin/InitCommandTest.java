package com.coderhino.commands.builtin;

import com.coderhino.commands.CommandContext;
import com.coderhino.commands.CommandRegistry;
import com.coderhino.commands.PromptCommandExecutor;
import com.coderhino.cli.PrintStreamTerminalRenderer;
import com.coderhino.services.ServiceRegistry;
import com.coderhino.services.lsp.LspClientManager;
import com.coderhino.services.mcp.McpConnectionManager;
import com.coderhino.services.tasks.TaskService;
import com.coderhino.state.AppState;
import com.coderhino.state.BootstrapState;
import com.coderhino.state.SessionRuntime;
import com.coderhino.state.SessionStore;
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
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InitCommandTest {

    @Test
    void initDelegatesToPromptExecutorWithRepositoryAwarePrompt(@TempDir Path tempDir) {
        var command = new InitCommand();
        var capturedPrompt = new AtomicReference<String>();
        var outBuffer = new ByteArrayOutputStream();
        var errBuffer = new ByteArrayOutputStream();
        var context = context(tempDir, (ctx, definition, prompt) -> {
            capturedPrompt.set(prompt);
            return "init result";
        }, outBuffer, errBuffer);

        command.execute(context, "");

        assertEquals("init result", outBuffer.toString().trim());
        assertTrue(errBuffer.toString().isBlank());
        assertTrue(capturedPrompt.get().contains("repository-aware initialization workflow"));
        assertTrue(capturedPrompt.get().contains("ask_user_question"));
        assertTrue(capturedPrompt.get().contains("CLAUDE.md"));
        assertTrue(capturedPrompt.get().contains("CLAUDE.local.md"));
        assertTrue(capturedPrompt.get().contains(".claude/skills"));
        assertTrue(capturedPrompt.get().contains(".coderhino/hooks.json"));
        assertTrue(capturedPrompt.get().contains("Ask follow-up questions only for information the codebase cannot answer reliably."));
        assertTrue(capturedPrompt.get().contains("Never claim a file was created if you only analyzed the repository or proposed changes."));
    }

    @Test
    void initReportsUnavailablePromptExecutionClearly(@TempDir Path tempDir) {
        var outBuffer = new ByteArrayOutputStream();
        var errBuffer = new ByteArrayOutputStream();
        var context = context(tempDir, null, outBuffer, errBuffer);

        new InitCommand().execute(context, "");

        assertTrue(outBuffer.toString().isBlank());
        assertTrue(errBuffer.toString().contains("Prompt command execution is unavailable for /init"));
    }

    private static CommandContext context(
        Path tempDir,
        PromptCommandExecutor promptExecutor,
        ByteArrayOutputStream outBuffer,
        ByteArrayOutputStream errBuffer
    ) {
        return new CommandContext(
            bootstrapState(tempDir),
            new CommandRegistry(List.of()),
            new SessionStore(new ObjectMapper().registerModule(new JavaTimeModule()), tempDir.resolve("sessions")),
            new ServiceRegistry(new McpConnectionManager(), new LspClientManager(), new TaskService(tempDir.resolve("tasks.json"))),
            promptExecutor,
            new PrintStreamTerminalRenderer(new PrintStream(outBuffer, true), new PrintStream(errBuffer, true)),
            new PrintStream(outBuffer, true),
            new PrintStream(errBuffer, true)
        );
    }

    private static BootstrapState bootstrapState(Path cwd) {
        return new BootstrapState(new AppState(
            false,
            "MiniMax-M2.5",
            cwd.toAbsolutePath().normalize().toString(),
            false,
            true,
            PermissionMode.BYPASS,
            0.0,
            new SessionRuntime(UUID.randomUUID(), null, null, List.of(), List.of(), List.of()),
            List.of()
        ));
    }
}
