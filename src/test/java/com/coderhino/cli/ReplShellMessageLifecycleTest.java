package com.coderhino.cli;

import com.coderhino.commands.CommandRegistry;
import com.coderhino.context.ContextCollector;
import com.coderhino.permissions.PermissionChecker;
import com.coderhino.query.ModelClient;
import com.coderhino.query.ModelResponse;
import com.coderhino.query.QueryEngine;
import com.coderhino.query.QueryRequest;
import com.coderhino.services.ServiceRegistry;
import com.coderhino.state.BootstrapState;
import com.coderhino.state.SessionRuntime;
import com.coderhino.tools.ToolRegistry;
import com.coderhino.types.Message;
import com.coderhino.types.PermissionMode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ReplShellMessageLifecycleTest {

    @Test
    void cliExchangeProducesExactlyOnePersistedAssistantMessage() throws Exception {
        var tempDir = Files.createTempDirectory("repl-test");

        var sessionRuntime = SessionRuntime.create();
        var appState = new com.coderhino.state.AppState(
            false, "test-model", tempDir.toString(), true, true,
            PermissionMode.BYPASS, 0.0, sessionRuntime, List.of()
        );
        var bootstrapState = new BootstrapState(appState);

        var modelClient = (ModelClient) (state, request) -> new ModelResponse.AssistantReply("model reply");
        var queryEngine = new QueryEngine(
            ToolRegistry.createDefault(),
            modelClient,
            new PermissionChecker(),
            new ContextCollector(),
            ServiceRegistry.createDefault()
        );

        var sessionStore = new com.coderhino.state.SessionStore(
            new ObjectMapper().registerModule(new JavaTimeModule()), tempDir
        );

        var input = "hello\n";
        var outBuffer = new ByteArrayOutputStream();
        var out = new PrintStream(outBuffer, true, StandardCharsets.UTF_8);
        var errBuffer = new ByteArrayOutputStream();
        var err = new PrintStream(errBuffer, true, StandardCharsets.UTF_8);

        var shell = new ReplShell(
            bootstrapState,
            CommandRegistry.createDefault(),
            ToolRegistry.createDefault(),
            queryEngine,
            sessionStore,
            ServiceRegistry.createDefault(),
            new ByteArrayInputStream(input.getBytes(StandardCharsets.UTF_8)),
            out,
            err
        );

        shell.run();

        var messages = bootstrapState.get().messages();
        long assistantCount = messages.stream()
            .filter(m -> m instanceof Message.AssistantMessage)
            .count();
        assertEquals(1, assistantCount,
            "CLI exchange should produce exactly one assistant message in bootstrap state, but found: " + messages);
    }
}
