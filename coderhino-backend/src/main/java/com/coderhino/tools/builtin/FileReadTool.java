package com.coderhino.tools.builtin;

import com.coderhino.tools.ToolContext;
import com.coderhino.tools.ToolDefinition;
import com.coderhino.types.PermissionResult;
import com.coderhino.types.ToolInputSchema;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public final class FileReadTool implements ToolDefinition<FileReadTool.Input, String> {
    private static final int MAX_FILE_SIZE_BYTES = 100 * 1024;

    @Override
    public String name() {
        return "read_file";
    }

    @Override
    public String description() {
        return "Read a UTF-8 text file with numbered lines";
    }

    @Override
    public ToolInputSchema inputSchema() {
        return ToolInputSchema.object(Map.of(
            "path", Map.of("type", "string"),
            "offset", Map.of("type", "integer"),
            "limit", Map.of("type", "integer")
        ));
    }

    @Override
    public boolean isReadOnly() {
        return true;
    }

    @Override
    public PermissionResult validate(Input input, ToolContext context) {
        if (input.path() == null || input.path().isBlank()) {
            return PermissionResult.deny("Path must not be blank.");
        }
        return PermissionResult.allow();
    }

    @Override
    public String execute(Input input, ToolContext context) throws Exception {
        var target = resolve(context, input.path());

        if (!Files.exists(target)) {
            throw new IOException("File not found: " + target);
        }

        var rawContent = Files.readString(target, StandardCharsets.UTF_8);

        if (rawContent.length() > MAX_FILE_SIZE_BYTES) {
            rawContent = rawContent.substring(0, MAX_FILE_SIZE_BYTES) + System.lineSeparator() + "... [truncated at 100KB]";
        }

        var lines = rawContent.split("\\R", -1);
        int start = Math.max(0, (input.offset() == null ? 1 : input.offset()) - 1);
        int limit = input.limit() == null ? lines.length : Math.max(0, input.limit());
        int end = Math.min(lines.length, start + limit);

        var sb = new StringBuilder();
        for (int i = start; i < end; i++) {
            if (sb.length() > 0) {
                sb.append(System.lineSeparator());
            }
            sb.append("%d: %s".formatted(i + 1, lines[i]));
        }

        return sb.toString();
    }

    private Path resolve(ToolContext context, String rawPath) {
        var path = Path.of(rawPath);
        if (path.isAbsolute()) {
            return path.normalize();
        }
        return Path.of(context.bootstrapState().get().cwd()).resolve(path).normalize();
    }

    public record Input(String path, Integer offset, Integer limit) {
    }
}
