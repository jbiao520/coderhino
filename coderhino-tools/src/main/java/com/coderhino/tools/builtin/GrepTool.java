package com.coderhino.tools.builtin;

import com.coderhino.tools.ToolContext;
import com.coderhino.tools.ToolDefinition;
import com.coderhino.types.PermissionResult;
import com.coderhino.types.ToolInputSchema;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import java.util.stream.Stream;

public final class GrepTool implements ToolDefinition<GrepTool.Input, GrepTool.Output> {
    private static final int DEFAULT_HEAD_LIMIT = 250;
    private static final int MAX_RESULTS = 1000;
    private static final int MAX_LINE_LENGTH = 500;
    private static final Set<String> EXCLUDED_DIRS = Set.of(".git", ".svn", ".hg", ".bzr", ".jj", ".sl", "node_modules", ".gradle", "target", "__pycache__");

    @Override
    public String name() {
        return "grep";
    }

    @Override
    public String description() {
        return "Search file contents with a regular expression pattern";
    }

    @Override
    public boolean isReadOnly() {
        return true;
    }

    @Override
    public ToolInputSchema inputSchema() {
        return ToolInputSchema.object(Map.of(
            "pattern", Map.of("type", "string"),
            "basePath", Map.of("type", "string"),
            "path", Map.of("type", "string"),
            "glob", Map.of("type", "string"),
            "output_mode", Map.of("type", "string"),
            "context", Map.of("type", "integer"),
            "head_limit", Map.of("type", "integer"),
            "case_insensitive", Map.of("type", "boolean")
        ));
    }

    @Override
    public PermissionResult validate(Input input, ToolContext context) {
        if (input.pattern() == null || input.pattern().isBlank()) {
            return PermissionResult.deny("Pattern must not be blank.");
        }
        try {
            int flags = Boolean.TRUE.equals(input.caseInsensitive()) ? Pattern.CASE_INSENSITIVE : 0;
            Pattern.compile(input.pattern(), flags);
        } catch (PatternSyntaxException e) {
            return PermissionResult.deny("Invalid regex pattern: " + e.getMessage());
        }
        return PermissionResult.allow();
    }

    @Override
    public Output execute(Input input, ToolContext context) throws Exception {
        var rawBasePath = input.basePath() == null || input.basePath().isBlank() ? input.path() : input.basePath();
        var basePath = rawBasePath == null || rawBasePath.isBlank()
            ? Path.of(context.bootstrapState().cwd())
            : resolve(context, rawBasePath);

        if (!Files.exists(basePath)) {
            return new Output("content", 0, List.of(), "Path does not exist: " + basePath, 0, 0, false);
        }

        boolean ci = Boolean.TRUE.equals(input.caseInsensitive());
        int flags = ci ? Pattern.CASE_INSENSITIVE : 0;
        var pattern = Pattern.compile(input.pattern(), flags);
        var mode = input.outputMode() == null ? "files_with_matches" : input.outputMode();
        var contextLines = input.context() == null ? 0 : Math.max(0, input.context());
        var headLimit = input.headLimit() == null ? DEFAULT_HEAD_LIMIT : Math.max(0, input.headLimit());
        // 0 means unlimited for head_limit
        int effectiveLimit = headLimit == 0 ? MAX_RESULTS : Math.min(headLimit, MAX_RESULTS);

        var globPattern = input.glob();

        if ("files_with_matches".equals(mode)) {
            return executeFilesMode(basePath, pattern, globPattern, effectiveLimit);
        } else if ("count".equals(mode)) {
            return executeCountMode(basePath, pattern, globPattern, effectiveLimit);
        } else {
            return executeContentMode(basePath, pattern, globPattern, contextLines, effectiveLimit);
        }
    }

    private Output executeFilesMode(Path basePath, Pattern pattern, String globPattern, int limit) throws Exception {
        var files = new ArrayList<String>();

        try (Stream<Path> paths = Files.walk(basePath)) {
            var fileList = paths
                .filter(Files::isRegularFile)
                .filter(p -> !isExcluded(p, basePath))
                .filter(p -> matchesGlob(p, basePath, globPattern))
                .toList();

            for (Path path : fileList) {
                if (files.size() >= limit) break;
                try {
                    var content = Files.readString(path, StandardCharsets.UTF_8);
                    if (pattern.matcher(content).find()) {
                        files.add(path.toAbsolutePath().normalize().toString());
                    }
                } catch (Exception ignored) {
                    // Skip unreadable files
                }
            }
        }

        boolean truncated = files.size() >= limit;
        return new Output("files_with_matches", files.size(), files, null, 0, 0, truncated);
    }

    private Output executeCountMode(Path basePath, Pattern pattern, String globPattern, int limit) throws Exception {
        var lines = new ArrayList<String>();
        int totalMatches = 0;
        int fileCount = 0;

        try (Stream<Path> paths = Files.walk(basePath)) {
            var fileList = paths
                .filter(Files::isRegularFile)
                .filter(p -> !isExcluded(p, basePath))
                .filter(p -> matchesGlob(p, basePath, globPattern))
                .toList();

            for (Path path : fileList) {
                if (fileCount >= limit) break;
                try {
                    var content = Files.readString(path, StandardCharsets.UTF_8);
                    var matcher = pattern.matcher(content);
                    int count = 0;
                    while (matcher.find()) count++;
                    if (count > 0) {
                        lines.add(path.toAbsolutePath().normalize() + ":" + count);
                        totalMatches += count;
                        fileCount++;
                    }
                } catch (Exception ignored) {
                    // Skip unreadable files
                }
            }
        }

        boolean truncated = fileCount >= limit;
        var content = String.join(System.lineSeparator(), lines);
        return new Output("count", fileCount, List.of(), content.isEmpty() ? null : content, 0, totalMatches, truncated);
    }

    private Output executeContentMode(Path basePath, Pattern pattern, String globPattern, int contextLines, int limit) throws Exception {
        var resultLines = new ArrayList<String>();
        int totalFiles = 0;

        try (Stream<Path> paths = Files.walk(basePath)) {
            var fileList = paths
                .filter(Files::isRegularFile)
                .filter(p -> !isExcluded(p, basePath))
                .filter(p -> matchesGlob(p, basePath, globPattern))
                .toList();

            for (Path path : fileList) {
                if (resultLines.size() >= limit) break;
                try {
                    var allLines = Files.readAllLines(path, StandardCharsets.UTF_8);
                    var matchIndices = new ArrayList<Integer>();
                    for (int i = 0; i < allLines.size(); i++) {
                        if (pattern.matcher(allLines.get(i)).find()) {
                            matchIndices.add(i);
                        }
                    }

                    if (!matchIndices.isEmpty()) {
                        totalFiles++;
                        // Collect lines with context, using a set to avoid duplicates
                        var lineSet = new LinkedHashSet<Integer>();
                        for (int idx : matchIndices) {
                            int start = Math.max(0, idx - contextLines);
                            int end = Math.min(allLines.size() - 1, idx + contextLines);
                            for (int i = start; i <= end; i++) {
                                lineSet.add(i);
                            }
                        }

                        var sortedIndices = new ArrayList<>(lineSet);
                        java.util.Collections.sort(sortedIndices);

                        for (int lineIdx : sortedIndices) {
                            if (resultLines.size() >= limit) break;
                            var line = allLines.get(lineIdx);
                            if (line.length() > MAX_LINE_LENGTH) {
                                line = line.substring(0, MAX_LINE_LENGTH) + "... [truncated]";
                            }
                            resultLines.add("%s:%d:%s".formatted(
                                path.toAbsolutePath().normalize(), lineIdx + 1, line));
                        }
                    }
                } catch (Exception ignored) {
                    // Skip unreadable files
                }
            }
        }

        boolean truncated = resultLines.size() >= limit;
        var content = String.join(System.lineSeparator(), resultLines);
        return new Output("content", totalFiles, List.of(), content, resultLines.size(), 0, truncated);
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

    private boolean matchesGlob(Path path, Path basePath, String globPattern) {
        if (globPattern == null || globPattern.isBlank()) {
            return true;
        }
        try {
            var matcher = java.nio.file.FileSystems.getDefault().getPathMatcher("glob:" + globPattern);
            var relative = basePath.relativize(path);
            // Match against full relative path and just filename
            return matcher.matches(relative) || matcher.matches(path.getFileName());
        } catch (Exception e) {
            return true; // Invalid glob pattern — don't filter
        }
    }

    private Path resolve(ToolContext context, String rawPath) {
        var path = Path.of(rawPath);
        if (path.isAbsolute()) {
            return path.normalize();
        }
        return Path.of(context.bootstrapState().cwd()).resolve(path).normalize();
    }

    public record Input(String pattern, String basePath, String path, String glob,
                        @JsonProperty("output_mode") String outputMode,
                        Integer context,
                        @JsonProperty("head_limit") Integer headLimit,
                        @JsonProperty("case_insensitive") Boolean caseInsensitive) {
    }

    public record Output(String mode, int numFiles, List<String> filenames, String content,
                         int numLines, int numMatches, boolean truncated) {
    }
}
