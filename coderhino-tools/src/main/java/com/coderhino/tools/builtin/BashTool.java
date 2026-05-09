package com.coderhino.tools.builtin;

import com.coderhino.permissions.EnhancedPermissionChecker;
import com.coderhino.tools.ToolContext;
import com.coderhino.tools.ToolDefinition;
import com.coderhino.types.PermissionMode;
import com.coderhino.types.PermissionResult;
import com.coderhino.types.ToolInputSchema;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

public final class BashTool implements ToolDefinition<BashTool.Input, BashTool.Output> {
    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(30);
    private static final int MAX_OUTPUT_SIZE = 100 * 1024;
    private static final EnhancedPermissionChecker PERMISSION_CHECKER = new EnhancedPermissionChecker();

    @Override
    public String name() {
        return "bash";
    }

    @Override
    public String description() {
        return "Execute a shell command in the working directory";
    }

    @Override
    public ToolInputSchema inputSchema() {
        return ToolInputSchema.object(Map.of(
            "command", Map.of("type", "string"),
            "timeoutSeconds", Map.of("type", "integer")
        ));
    }

    @Override
    public PermissionResult validate(Input input, ToolContext context) {
        if (input.command() == null || input.command().isBlank()) {
            return PermissionResult.deny("Command must not be blank.");
        }

        PermissionMode mode = context.permissionMode();
        PermissionResult baseResult = switch (mode) {
            case BYPASS -> PermissionResult.allow();
            case PLAN -> PermissionResult.deny("Bash execution is not allowed in PLAN mode.");
            case DEFAULT, AUTO, DONT_ASK, ACCEPT_EDITS -> PermissionResult.ask("Bash execution requires confirmation.");
        };

        return PERMISSION_CHECKER.resolveWithContext(
            mode,
            baseResult,
            name(),
            new EnhancedPermissionChecker.BashToolInput(input.command(), input.timeoutSeconds())
        );
    }

    @Override
    public Output execute(Input input, ToolContext context) throws Exception {
        var cwd = Path.of(context.bootstrapState().cwd());
        var timeout = input.timeoutSeconds() == null ? DEFAULT_TIMEOUT : Duration.ofSeconds(Math.max(1, input.timeoutSeconds()));
        var process = new ProcessBuilder("/bin/zsh", "-lc", input.command())
            .directory(cwd.toFile())
            .start();

        var executor = Executors.newFixedThreadPool(2);
        Future<String> stdoutFuture = executor.submit(() -> readWithLimit(process.getInputStream(), MAX_OUTPUT_SIZE));
        Future<String> stderrFuture = executor.submit(() -> readWithLimit(process.getErrorStream(), MAX_OUTPUT_SIZE));
        try {
            var finished = process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS);
            if (!finished) {
                process.destroyForcibly();
                process.waitFor(5, TimeUnit.SECONDS);
                var stdout = readFuture(stdoutFuture);
                var stderr = appendLine(readFuture(stderrFuture), "Command timed out after %d seconds".formatted(timeout.toSeconds()));
                return new Output(-1, stdout, stderr);
            }

            var stdout = readFuture(stdoutFuture);
            var stderr = readFuture(stderrFuture);
            return new Output(process.exitValue(), stdout, stderr);
        } finally {
            executor.shutdownNow();
        }
    }

    private String readWithLimit(InputStream inputStream, int maxSize) throws Exception {
        var sb = new StringBuilder();
        var truncated = false;
        try (var reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (sb.length() < maxSize) {
                    if (sb.length() > 0) {
                        sb.append(System.lineSeparator());
                    }
                    var remaining = maxSize - sb.length();
                    if (line.length() > remaining) {
                        sb.append(line, 0, remaining);
                        truncated = true;
                    } else {
                        sb.append(line);
                    }
                } else {
                    truncated = true;
                }
            }
        }
        if (truncated) {
            if (sb.length() > 0) {
                sb.append(System.lineSeparator());
            }
            sb.append("... [truncated]");
        }
        return sb.toString();
    }

    private String readFuture(Future<String> future) throws Exception {
        try {
            return future.get();
        } catch (ExecutionException e) {
            var cause = e.getCause();
            if (cause instanceof Exception exception) {
                throw exception;
            }
            throw e;
        }
    }

    private String appendLine(String existing, String line) {
        if (existing == null || existing.isEmpty()) {
            return line;
        }
        return existing + System.lineSeparator() + line;
    }

    public record Input(String command, Integer timeoutSeconds) {
    }

    public record Output(int exitCode, String stdout, String stderr) {
    }
}
