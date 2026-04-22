package com.coderhino.cli;

import com.coderhino.commands.CommandRegistry;
import com.coderhino.query.ModelClientFactory;
import com.coderhino.query.QueryEngine;
import com.coderhino.server.ServerMode;
import com.coderhino.services.ServiceRegistry;
import com.coderhino.state.AppState;
import com.coderhino.state.BootstrapState;
import com.coderhino.state.LifecycleManager;
import com.coderhino.state.SessionRuntime;
import com.coderhino.state.SessionStore;
import com.coderhino.tools.ToolRegistry;
import com.coderhino.types.PermissionMode;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.nio.file.Path;

@Command(
    name = "coderhino",
    version = "coderhino 1.0.0-SNAPSHOT",
    description = "Coderhino CLI - AI-assisted coding tool",
    mixinStandardHelpOptions = true
)
public class Main implements Runnable {

    @Option(names = { "-v", "--verbose" }, description = "Enable verbose output")
    private boolean verbose = false;

    @Option(names = { "-m", "--model" }, description = "Main model name")
    private String model = "sonnet";

    @Option(names = { "--print-state" }, description = "Print bootstrap state instead of starting the REPL")
    private boolean printState = false;

    @Option(names = { "--permission-mode" }, description = "Permission mode: ${COMPLETION-CANDIDATES}", defaultValue = "BYPASS")
    private PermissionMode permissionMode = PermissionMode.BYPASS;

    @Option(names = { "--serve" }, description = "Start web API server instead of REPL")
    private boolean serve = false;

    @Option(names = { "--port" }, description = "Port for web server", defaultValue = "8080")
    private int port = 8080;

    @Override
    public void run() {
        var initialState = new AppState(
            verbose,
            model,
            Path.of("").toAbsolutePath().normalize().toString(),
            true,
            true,
            permissionMode,
            0.0,
            SessionRuntime.create(),
            java.util.List.of()
        );
        var bootstrapState = new BootstrapState(initialState);
        var lifecycle = new LifecycleManager();

        if (printState) {
            System.out.println(bootstrapState.get());
            return;
        }

        if (serve) {
            var serviceRegistry = ServiceRegistry.createDefault(Path.of("").toAbsolutePath().normalize());
            serviceRegistry.serverService().start(ServerMode.API, port);
            System.out.printf("Code Rhino API server running on http://127.0.0.1:%d%n", port);
            try {
                Thread.currentThread().join();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return;
        }

        var sessionStore = new SessionStore();

        lifecycle.registerStartupHook(() -> {
            System.out.printf("Coderhino (model=%s)%n", model);
            System.out.println("Type /help for commands. Type /exit to quit.");
            if (verbose) {
                System.out.println("Verbose mode enabled.");
            }
        });

        lifecycle.registerShutdownHook(() -> {
            var current = bootstrapState.get();
            if (current.running()) {
                bootstrapState.stop();
            }
        });

        lifecycle.start();

        var cwd = Path.of("").toAbsolutePath().normalize();
        var registry = CommandRegistry.createDefault(cwd);
        var toolRegistry = ToolRegistry.createDefault();
        var serviceRegistry = ServiceRegistry.createDefault(cwd);
        var modelClient = ModelClientFactory.create(model);
        var queryEngine = new QueryEngine(toolRegistry, modelClient, new com.coderhino.permissions.PermissionChecker(), new com.coderhino.context.ContextCollector(), serviceRegistry);
        var shell = new ReplShell(bootstrapState, registry, toolRegistry, queryEngine, sessionStore, serviceRegistry, System.in, System.out, System.err);

        try {
            shell.run();
        } finally {
            lifecycle.shutdown();
        }
    }

    public static void main(String[] args) {
        int exitCode = new CommandLine(new Main()).execute(args);
        System.exit(exitCode);
    }
}
