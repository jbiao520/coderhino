package com.coderhino.tools.builtin;

import com.coderhino.permissions.EnhancedPermissionChecker;
import com.coderhino.tools.ToolContext;
import com.coderhino.tools.ToolDefinition;
import com.coderhino.types.PermissionMode;
import com.coderhino.types.PermissionResult;
import com.coderhino.types.ToolInputSchema;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
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
        var cwd = Path.of(context.bootstrapState().get().cwd());
        var timeout = input.timeoutSeconds() == null ? DEFAULT_TIMEOUT : Duration.ofSeconds(Math.max(1, input.timeoutSeconds()));
        var process = new ProcessBuilder("/bin/zsh", "-lc", input.command())
            .directory(cwd.toFile())
            .start();

        var finished = process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS);
        if (!finished) {
            process.destroyForcibly();
            return new Output(-1, "", "Command timed out after %d seconds".formatted(timeout.toSeconds()));
        }

        try (var stdoutReader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8));
             var stderrReader = new BufferedReader(new InputStreamReader(process.getErrorStream(), StandardCharsets.UTF_8))) {
            var stdout = readWithLimit(stdoutReader, MAX_OUTPUT_SIZE);
            var stderr = readWithLimit(stderrReader, MAX_OUTPUT_SIZE);
            return new Output(process.exitValue(), stdout, stderr);
        }
    }

    private String readWithLimit(BufferedReader reader, int maxSize) throws Exception {
        var sb = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            if (sb.length() > 0) {
                sb.append(System.lineSeparator());
            }
            if (sb.length() + line.length() > maxSize) {
                sb.append(line, 0, maxSize - sb.length());
                sb.append(System.lineSeparator()).append("... [truncated]");
                break;
            }
            sb.append(line);
        }
        return sb.toString();
    }

    public record Input(String command, Integer timeoutSeconds) {
    }

    public record Output(int exitCode, String stdout, String stderr) {
    }
}
