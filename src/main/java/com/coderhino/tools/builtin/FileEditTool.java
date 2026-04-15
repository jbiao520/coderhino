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

public final class FileEditTool implements ToolDefinition<FileEditTool.Input, String> {
    private static final EnhancedPermissionChecker PERMISSION_CHECKER = new EnhancedPermissionChecker();

    @Override
    public String name() {
        return "edit_file";
    }

    @Override
    public String description() {
        return "Replace text in a UTF-8 file";
    }

    @Override
    public ToolInputSchema inputSchema() {
        return ToolInputSchema.object(Map.of(
            "path", Map.of("type", "string"),
            "oldText", Map.of("type", "string"),
            "newText", Map.of("type", "string")
        ));
    }

    @Override
    public PermissionResult validate(Input input, ToolContext context) {
        if (input.path() == null || input.path().isBlank()) {
            return PermissionResult.deny("Path must not be blank.");
        }
        if (input.oldText() == null || input.oldText().isEmpty()) {
            return PermissionResult.deny("oldText must not be empty.");
        }

        PermissionMode mode = context.permissionMode();
        PermissionResult baseResult = switch (mode) {
            case BYPASS -> PermissionResult.allow();
            case PLAN -> PermissionResult.deny("File editing is not allowed in PLAN mode.");
            case DEFAULT, AUTO, DONT_ASK, ACCEPT_EDITS -> PermissionResult.ask("Editing files requires confirmation.");
        };

        return PERMISSION_CHECKER.resolveWithContext(
            mode,
            baseResult,
            name(),
            new EnhancedPermissionChecker.EditToolInput(input.path(), input.oldText(), input.newText())
        );
    }

    @Override
    public String execute(Input input, ToolContext context) throws Exception {
        var target = resolve(context, input.path());

        if (!Files.exists(target)) {
            return "Error: File not found: " + target;
        }

        var current = Files.readString(target, StandardCharsets.UTF_8);
        var oldText = input.oldText();

        int firstIndex = current.indexOf(oldText);
        if (firstIndex < 0) {
            return "Error: oldText not found in " + target;
        }

        int secondIndex = current.indexOf(oldText, firstIndex + 1);
        if (secondIndex >= 0) {
            return "Error: oldText found multiple times in " + target + " (at positions " + firstIndex + " and " + secondIndex + "). Provide more context to make it unique.";
        }

        var updated = current.replace(oldText, input.newText() == null ? "" : input.newText());
        Files.writeString(target, updated, StandardCharsets.UTF_8);
        return "Updated " + target;
    }

    private Path resolve(ToolContext context, String rawPath) {
        var path = Path.of(rawPath);
        if (path.isAbsolute()) {
            return path.normalize();
        }
        return Path.of(context.bootstrapState().get().cwd()).resolve(path).normalize();
    }

    public record Input(String path, String oldText, String newText) {
    }
}
