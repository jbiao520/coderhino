package com.coderhino.tools.builtin;

import com.coderhino.permissions.EnhancedPermissionChecker;
import com.coderhino.tools.ToolContext;
import com.coderhino.tools.ToolDefinition;
import com.coderhino.types.PermissionMode;
import com.coderhino.types.PermissionResult;
import com.coderhino.types.ToolInputSchema;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

public final class FileWriteTool implements ToolDefinition<FileWriteTool.Input, String> {
    private static final EnhancedPermissionChecker PERMISSION_CHECKER = new EnhancedPermissionChecker();

    @Override
    public String name() {
        return "write_file";
    }

    @Override
    public String description() {
        return "Write UTF-8 text content to a file";
    }

    @Override
    public ToolInputSchema inputSchema() {
        return ToolInputSchema.object(Map.of(
            "path", Map.of("type", "string"),
            "content", Map.of("type", "string")
        ));
    }

    @Override
    public PermissionResult validate(Input input, ToolContext context) {
        if (input.path() == null || input.path().isBlank()) {
            return PermissionResult.deny("Path must not be blank.");
        }

        PermissionMode mode = context.permissionMode();
        PermissionResult baseResult = switch (mode) {
            case BYPASS -> PermissionResult.allow();
            case PLAN -> PermissionResult.deny("File writing is not allowed in PLAN mode.");
            case DEFAULT, AUTO, DONT_ASK, ACCEPT_EDITS -> PermissionResult.ask("Writing files requires confirmation.");
        };

        return PERMISSION_CHECKER.resolveWithContext(
            mode,
            baseResult,
            name(),
            new EnhancedPermissionChecker.FileToolInput(input.path(), input.content())
        );
    }

    @Override
    public String execute(Input input, ToolContext context) throws Exception {
        var target = resolve(context, input.path());
        var parent = target.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Files.writeString(target, input.content() == null ? "" : input.content(), StandardCharsets.UTF_8);
        return "Wrote %d chars to %s".formatted(input.content() == null ? 0 : input.content().length(), target);
    }

    private Path resolve(ToolContext context, String rawPath) {
        var path = Path.of(rawPath);
        if (path.isAbsolute()) {
            return path.normalize();
        }
        return Path.of(context.bootstrapState().get().cwd()).resolve(path).normalize();
    }

    public record Input(String path, String content) {
    }
}
