package com.coderhino.commands.builtin;

import com.coderhino.commands.CommandContext;
import com.coderhino.commands.CommandRegistry;
import com.coderhino.commands.PromptCommandExecutor;
import com.coderhino.cli.PrintStreamTerminalRenderer;
import com.coderhino.services.ServiceRegistry;
import com.coderhino.services.lsp.LspClientManager;
import com.coderhino.services.mcp.McpConnectionManager;
import com.coderhino.services.mcp.McpServerDefinition;
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
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertTrue;

class McpCommandTest {

    @Test
    void toolsCommandSurfacesProtocolFailureStatus(@TempDir Path tempDir) throws Exception {
        var mcp = new McpConnectionManager();
        var script = tempDir.resolve("stderr-only-mcp.sh");
        Files.writeString(script, "#!/bin/sh\necho 'missing display' >&2\nsleep 1\n");
        script.toFile().setExecutable(true);
        mcp.register(new McpServerDefinition("playwright", script.toString(), List.of(), java.util.Map.of(), true, 50L));

        var outBuffer = new ByteArrayOutputStream();
        var errBuffer = new ByteArrayOutputStream();
        var context = new CommandContext(
            bootstrapState(tempDir),
            new CommandRegistry(List.of()),
            new SessionStore(new ObjectMapper().registerModule(new JavaTimeModule()), tempDir.resolve("sessions")),
            new ServiceRegistry(mcp, new LspClientManager(), new TaskService(tempDir.resolve("tasks.json"))),
            noPromptExecutor(),
            new PrintStreamTerminalRenderer(new PrintStream(outBuffer, true), new PrintStream(errBuffer, true)),
            new PrintStream(outBuffer, true),
            new PrintStream(errBuffer, true)
        );

        new McpCommand().execute(context, "tools playwright");

        var stderr = errBuffer.toString();
        assertTrue(stderr.contains("MCP server playwright status=protocol-startup-failed:"));
        assertTrue(stderr.contains("stderr: missing display"));
        assertTrue(stderr.contains("connected=true"));
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

    private static PromptCommandExecutor noPromptExecutor() {
        return (context, definition, prompt) -> "";
    }
}
