package com.coderhino.cli;

import com.coderhino.commands.CommandContext;
import com.coderhino.commands.CommandRegistry;
import com.coderhino.query.QueryEngine;
import com.coderhino.services.ServiceRegistry;
import com.coderhino.services.summary.FileChangeSummary;
import com.coderhino.services.summary.FileChangeSummaryFormatter;
import com.coderhino.services.summary.SessionEndSummary;
import com.coderhino.state.BootstrapState;
import com.coderhino.state.SessionStore;
import com.coderhino.tools.ToolRegistry;
import com.coderhino.types.Message;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

public final class ReplShell {
    private final BootstrapState bootstrapState;
    private final CommandRegistry commandRegistry;
    private final ToolRegistry toolRegistry;
    private final QueryEngine queryEngine;
    private final SessionStore sessionStore;
    private final ServiceRegistry serviceRegistry;
    private final BufferedReader reader;
    private final TerminalRenderer renderer;
    private final PrintStream commandOut;
    private final PrintStream commandErr;

    public ReplShell(
        BootstrapState bootstrapState,
        CommandRegistry commandRegistry,
        ToolRegistry toolRegistry,
        QueryEngine queryEngine,
        SessionStore sessionStore,
        ServiceRegistry serviceRegistry,
        InputStream input,
        PrintStream out,
        PrintStream err
    ) {
        this(bootstrapState, commandRegistry, toolRegistry, queryEngine, sessionStore,
             serviceRegistry, input, new ConsoleRenderer(out, err), out, err);
    }

    ReplShell(
        BootstrapState bootstrapState,
        CommandRegistry commandRegistry,
        ToolRegistry toolRegistry,
        QueryEngine queryEngine,
        SessionStore sessionStore,
        ServiceRegistry serviceRegistry,
        InputStream input,
        TerminalRenderer renderer,
        PrintStream commandOut,
        PrintStream commandErr
    ) {
        this.bootstrapState = bootstrapState;
        this.commandRegistry = commandRegistry;
        this.toolRegistry = toolRegistry;
        this.queryEngine = queryEngine;
        this.sessionStore = sessionStore;
        this.serviceRegistry = serviceRegistry;
        this.reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8));
        this.renderer = renderer;
        this.commandOut = commandOut;
        this.commandErr = commandErr;
    }

    public void run() {
        while (bootstrapState.get().running()) {
            renderer.printPrompt("coderhino> ");

            String line;
            try {
                line = reader.readLine();
            } catch (IOException exception) {
                renderer.printError("Failed to read input: " + exception.getMessage());
                bootstrapState.stop();
                return;
            }

            if (line == null) {
                bootstrapState.stop();
                renderer.printLine("");
                return;
            }

            var trimmed = line.trim();
            if (trimmed.isEmpty()) {
                continue;
            }

            if (trimmed.startsWith("/")) {
                dispatchCommand(trimmed.substring(1));
                continue;
            }

            var userMessage = new Message.UserMessage(trimmed);
            bootstrapState.update(state -> state.withSessionRuntime(state.sessionRuntime().append(sessionStore.recordMessage(state, userMessage))));
            bootstrapState.addMessage(userMessage);
            renderer.showSpinner("Thinking...");
            var response = queryEngine.respond(bootstrapState, trimmed);
            renderer.stopSpinner();
            renderer.printLine(response.content());
            renderer.printSectionHeader("---");
            bootstrapState.update(state -> state.withSessionRuntime(state.sessionRuntime().append(sessionStore.recordMessage(state, response))));
        }

        printFileChangeSummary();
    }

    private void printFileChangeSummary() {
        try {
            var sessionId = bootstrapState.get().sessionRuntime().sessionId();
            var summaryService = new SessionEndSummary(serviceRegistry.fileChangeTracker());
            var summary = summaryService.buildSummary(sessionId);
            if (summary.totalChanges() > 0) {
                renderer.printSectionHeader("---");
                renderer.printLine(FileChangeSummaryFormatter.format(summary));
            }
        } catch (Exception ignored) {
        }
    }

    private void dispatchCommand(String rawCommand) {
        var commandName = rawCommand;
        var args = "";
        var firstSpace = rawCommand.indexOf(' ');
        if (firstSpace >= 0) {
            commandName = rawCommand.substring(0, firstSpace);
            args = rawCommand.substring(firstSpace + 1).trim();
        }
        commandName = commandName.trim().toLowerCase();

        var command = commandRegistry.find(commandName);
        if (command.isEmpty()) {
            renderer.printError("Unknown command: /" + commandName);
            return;
        }

        var context = new CommandContext(
            bootstrapState,
            commandRegistry,
            sessionStore,
            serviceRegistry,
            this::executePromptCommand,
            renderer,
            commandOut,
            commandErr
        );
        command.get().execute(context, args);
    }

    private String executePromptCommand(CommandContext context, com.coderhino.commands.CommandDefinition definition, String prompt) {
        if (prompt == null || prompt.isBlank()) {
            return "";
        }
        renderer.showSpinner("Thinking...");
        try {
            var response = queryEngine.respond(context.bootstrapState(), prompt);
            context.bootstrapState().update(state -> state.withSessionRuntime(state.sessionRuntime().append(sessionStore.recordMessage(state, response))));
            return response.content();
        } finally {
            renderer.stopSpinner();
        }
    }
}
