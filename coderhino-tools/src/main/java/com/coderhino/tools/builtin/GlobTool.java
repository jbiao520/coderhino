package com.coderhino.tools.builtin;

import com.coderhino.tools.ToolContext;
import com.coderhino.tools.ToolDefinition;
import com.coderhino.types.PermissionResult;
import com.coderhino.types.ToolInputSchema;

import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

public final class GlobTool implements ToolDefinition<GlobTool.Input, GlobTool.Output> {
    private static final int MAX_RESULTS = 500;
    private static final Set<String> EXCLUDED_DIRS = Set.of(".git", ".svn", ".hg", ".bzr", ".jj", ".sl", "node_modules", ".gradle", "target", "__pycache__");

    @Override
    public String name() {
        return "glob";
    }

    @Override
    public String description() {
        return "Match files under a directory using a glob pattern";
    }

    @Override
    public boolean isReadOnly() {
        return true;
    }

    @Override
    public ToolInputSchema inputSchema() {
        return ToolInputSchema.object(Map.of(
            "pattern", Map.of("type", "string"),
            "basePath", Map.of("type", "string")
        ));
    }

    @Override
    public PermissionResult validate(Input input, ToolContext context) {
        if (input.pattern() == null || input.pattern().isBlank()) {
            return PermissionResult.deny("Pattern must not be blank.");
        }
        return PermissionResult.allow();
    }

    @Override
    public Output execute(Input input, ToolContext context) throws Exception {
        var basePath = input.basePath() == null || input.basePath().isBlank()
            ? Path.of(context.bootstrapState().cwd())
            : resolve(context, input.basePath());

        if (!Files.exists(basePath)) {
            return new Output(List.of(), 0, true);
        }

        var matcher = FileSystems.getDefault().getPathMatcher("glob:" + input.pattern());
        var results = new ArrayList<String>();

        try (Stream<Path> stream = Files.walk(basePath)) {
            stream
                .filter(Files::isRegularFile)
                .filter(path -> !isExcluded(path, basePath))
                .filter(path -> matcher.matches(basePath.relativize(path)) || matcher.matches(path.getFileName()))
                .sorted(Comparator.naturalOrder())
                .forEach(path -> {
                    if (results.size() < MAX_RESULTS) {
                        results.add(path.toAbsolutePath().normalize().toString());
                    }
                });
        }

        boolean truncated = results.size() >= MAX_RESULTS;
        return new Output(results, results.size(), truncated);
    }

    private boolean isExcluded(Path path, Path basePath) {
        var relative = basePath.relativize(path);
        for (int i = 0; i < relative.getNameCount(); i++) {
            if (EXCLUDED_DIRS.contains(relative.getName(i).toString())) {
                return true;
            }
        }
        return false;
    }

    private Path resolve(ToolContext context, String rawPath) {
        var path = Path.of(rawPath);
        if (path.isAbsolute()) {
            return path.normalize();
        }
        return Path.of(context.bootstrapState().cwd()).resolve(path).normalize();
    }

    public record Input(String pattern, String basePath) {
    }

    public record Output(List<String> filenames, int numFiles, boolean truncated) {
    }
}
