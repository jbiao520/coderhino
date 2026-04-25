package com.coderhino.agent;

import com.coderhino.context.ContextCollector;
import com.coderhino.permissions.PermissionChecker;
import com.coderhino.query.ModelClient;
import com.coderhino.query.ModelClientFactory;
import com.coderhino.query.QueryEngine;
import com.coderhino.query.QueryEventSink;
import com.coderhino.services.ServiceRegistry;
import com.coderhino.state.AppState;
import com.coderhino.state.BootstrapState;
import com.coderhino.state.SessionRuntime;
import com.coderhino.tools.ToolDefinition;
import com.coderhino.tools.ToolRegistry;
import com.coderhino.tools.ToolContext;
import com.coderhino.tools.runtime.ToolCommandRegistry;
import com.coderhino.types.Message;
import com.coderhino.types.PermissionMode;
import com.coderhino.types.PermissionResult;
import com.coderhino.types.ToolInputSchema;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import java.util.stream.Stream;

public final class CoderhinoAgent {
    private final AgentConfig config;
    private final BootstrapState managedState;

    private CoderhinoAgent(AgentConfig config) {
        this.config = config;
        this.managedState = config.bootstrapState() == null ? createBootstrapState(config) : config.bootstrapState();
    }

    public static Builder builder() {
        return new Builder();
    }

    public AgentResult run(String input) {
        return run(new AgentRequest(input));
    }

    public AgentResult run(AgentRequest request) {
        Objects.requireNonNull(request, "request");
        var state = request.bootstrapState() == null ? managedState : request.bootstrapState();
        var engine = createQueryEngine();
        var visibleInput = request.visibleInput() == null ? request.input() : request.visibleInput();
        var sink = request.eventSink() == null ? config.eventSink() : request.eventSink();
        var result = sink == null
            ? engine.execute(state, request.input(), visibleInput, NoOpAgentQueryEventSink.INSTANCE)
            : engine.execute(state, request.input(), visibleInput, sink);
        return new AgentResult(result.text(), result.stopReason(), result.iterationsUsed(), result.usage(), state.get(), state);
    }

    private enum NoOpAgentQueryEventSink implements QueryEventSink {
        INSTANCE;

        @Override public void onTextChunk(String chunk) {}
        @Override public void onStatus(String message) {}
        @Override public void onToolCall(String toolName, String toolUseId, String argumentsJson) {}
        @Override public void onToolResult(String toolName, String toolUseId, String result) {}
        @Override public void onUsage(long inputTokens, long outputTokens, long cacheCreationTokens, long cacheReadTokens) {}
        @Override public void onError(String error) {}
        @Override public void onCompleted(String finalText) {}
    }

    public AppState state() {
        return managedState.get();
    }

    public BootstrapState bootstrapState() {
        return managedState;
    }

    public AgentConfig config() {
        return config;
    }

    private QueryEngine createQueryEngine() {
        return new QueryEngine(
            config.toolRegistry(),
            config.modelClient(),
            config.permissionChecker(),
            new ContextCollector(),
            config.serviceRegistry(),
            config.customSystemPrompt(),
            config.appendSystemPrompt(),
            config.maxToolIterations(),
            config.maxBudgetUsd(),
            config.commandRegistry()
        );
    }

    private static BootstrapState createBootstrapState(AgentConfig config) {
        return new BootstrapState(new AppState(
            false,
            config.model(),
            config.cwd().toString(),
            false,
            true,
            config.permissionMode(),
            0.0,
            SessionRuntime.create(),
            List.of()
        ));
    }

    public record AgentRequest(String input, String visibleInput, QueryEventSink eventSink, BootstrapState bootstrapState) {
        public AgentRequest(String input) {
            this(input, input, null, null);
        }

        public AgentRequest {
            if (input == null || input.isBlank()) {
                throw new IllegalArgumentException("input is required");
            }
        }
    }

    public record AgentResult(
        String finalText,
        com.coderhino.query.QueryResult.StopReason stopReason,
        int iterationCount,
        com.coderhino.query.ModelResponse.Usage usage,
        AppState state,
        BootstrapState bootstrapState
    ) {
        public boolean isSuccess() {
            return stopReason == com.coderhino.query.QueryResult.StopReason.END_TURN;
        }

        public boolean isError() {
            return stopReason == com.coderhino.query.QueryResult.StopReason.ERROR;
        }
    }

    public record AgentConfig(
        ModelClient modelClient,
        String model,
        Path cwd,
        PermissionMode permissionMode,
        ToolRegistry toolRegistry,
        ServiceRegistry serviceRegistry,
        PermissionChecker permissionChecker,
        QueryEventSink eventSink,
        String customSystemPrompt,
        String appendSystemPrompt,
        int maxToolIterations,
        double maxBudgetUsd,
        BootstrapState bootstrapState,
        ToolCommandRegistry commandRegistry
    ) {
    }

    public static final class Builder {
        private ModelClient modelClient;
        private String model = "sonnet";
        private Path cwd = Path.of("").toAbsolutePath().normalize();
        private PermissionMode permissionMode = PermissionMode.DEFAULT;
        private ToolRegistry toolRegistry;
        private ServiceRegistry serviceRegistry;
        private PermissionChecker permissionChecker = new PermissionChecker();
        private QueryEventSink eventSink;
        private String customSystemPrompt;
        private String appendSystemPrompt;
        private int maxToolIterations = 200;
        private double maxBudgetUsd = 0.0;
        private BootstrapState bootstrapState;
        private ToolCommandRegistry commandRegistry;
        private String apiKey = System.getenv("ANTHROPIC_API_KEY");
        private String apiBaseUrl = System.getenv("ANTHROPIC_BASE_URL") == null ? "https://api.anthropic.com" : System.getenv("ANTHROPIC_BASE_URL");
        private com.coderhino.query.ProviderApiType providerApiType = com.coderhino.query.ProviderApiType.CLAUDE_CODE;
        private long contextWindow = ModelClientFactory.DEFAULT_CONTEXT_WINDOW;
        private long maxOutputTokens = ModelClientFactory.DEFAULT_MAX_OUTPUT_TOKENS;
        private final List<ToolDefinition<?, ?>> customTools = new ArrayList<>();

        public Builder modelClient(ModelClient modelClient) {
            this.modelClient = modelClient;
            return this;
        }

        public Builder model(String model) {
            this.model = model;
            return this;
        }

        public Builder cwd(Path cwd) {
            this.cwd = cwd == null ? this.cwd : cwd.toAbsolutePath().normalize();
            return this;
        }

        public Builder permissionMode(PermissionMode permissionMode) {
            this.permissionMode = permissionMode == null ? this.permissionMode : permissionMode;
            return this;
        }

        public Builder toolRegistry(ToolRegistry toolRegistry) {
            this.toolRegistry = toolRegistry;
            return this;
        }

        public Builder enabledBuiltInTools(List<String> toolNames) {
            this.toolRegistry = ToolRegistry.createDefault().filtered(toolNames);
            return this;
        }

        public Builder addTool(ToolDefinition<?, ?> tool) {
            this.customTools.add(Objects.requireNonNull(tool, "tool"));
            return this;
        }

        public Builder serviceRegistry(ServiceRegistry serviceRegistry) {
            this.serviceRegistry = serviceRegistry;
            return this;
        }

        public Builder permissionChecker(PermissionChecker permissionChecker) {
            this.permissionChecker = permissionChecker == null ? this.permissionChecker : permissionChecker;
            return this;
        }

        public Builder eventSink(QueryEventSink eventSink) {
            this.eventSink = eventSink;
            return this;
        }

        public Builder customSystemPrompt(String customSystemPrompt) {
            this.customSystemPrompt = customSystemPrompt;
            return this;
        }

        public Builder appendSystemPrompt(String appendSystemPrompt) {
            this.appendSystemPrompt = appendSystemPrompt;
            return this;
        }

        public Builder maxToolIterations(int maxToolIterations) {
            this.maxToolIterations = maxToolIterations;
            return this;
        }

        public Builder maxBudgetUsd(double maxBudgetUsd) {
            this.maxBudgetUsd = maxBudgetUsd;
            return this;
        }

        public Builder bootstrapState(BootstrapState bootstrapState) {
            this.bootstrapState = bootstrapState;
            return this;
        }

        public Builder commandRegistry(ToolCommandRegistry commandRegistry) {
            this.commandRegistry = commandRegistry;
            return this;
        }

        public Builder apiKey(String apiKey) {
            this.apiKey = apiKey;
            return this;
        }

        public Builder apiBaseUrl(String apiBaseUrl) {
            if (apiBaseUrl != null && !apiBaseUrl.isBlank()) {
                this.apiBaseUrl = apiBaseUrl;
            }
            return this;
        }

        public Builder providerApiType(com.coderhino.query.ProviderApiType providerApiType) {
            this.providerApiType = providerApiType == null ? this.providerApiType : providerApiType;
            return this;
        }

        public Builder contextWindow(long contextWindow) {
            this.contextWindow = contextWindow;
            return this;
        }

        public Builder maxOutputTokens(long maxOutputTokens) {
            this.maxOutputTokens = maxOutputTokens;
            return this;
        }

        public CoderhinoAgent build() {
            var resolvedServiceRegistry = serviceRegistry == null ? ServiceRegistry.createEmbeddedDefault(cwd) : serviceRegistry;
            var resolvedToolRegistry = toolRegistry == null ? confinedEmbeddedToolRegistry(cwd) : toolRegistry;
            resolvedToolRegistry = resolvedToolRegistry.withAll(customTools);
            var resolvedModelClient = modelClient == null
                ? ModelClientFactory.create(model, apiKey, apiBaseUrl, providerApiType, contextWindow, maxOutputTokens)
                : modelClient;
            var config = new AgentConfig(
                resolvedModelClient,
                model,
                cwd,
                permissionMode,
                resolvedToolRegistry,
                resolvedServiceRegistry,
                permissionChecker,
                eventSink,
                customSystemPrompt,
                appendSystemPrompt,
                maxToolIterations,
                maxBudgetUsd,
                bootstrapState,
                commandRegistry
            );
            return new CoderhinoAgent(config);
        }
    }

    private static ToolRegistry confinedEmbeddedToolRegistry(Path cwd) {
        var workspace = cwd.toAbsolutePath().normalize();
        return new ToolRegistry(List.of(
            new ConfinedFileReadTool(workspace),
            new ConfinedGlobTool(workspace),
            new ConfinedGrepTool(workspace)
        ));
    }

    private static Path confinedPath(Path workspace, String rawPath) {
        var path = Path.of(rawPath);
        var resolved = path.isAbsolute() ? path.normalize() : workspace.resolve(path).normalize();
        if (!resolved.startsWith(workspace)) {
            throw new IllegalArgumentException("Path must stay within workspace: " + workspace);
        }
        return resolved;
    }

    private static final class ConfinedFileReadTool implements ToolDefinition<ConfinedFileReadTool.Input, String> {
        private static final int MAX_FILE_SIZE_BYTES = 100 * 1024;
        private final Path workspace;

        private ConfinedFileReadTool(Path workspace) {
            this.workspace = workspace;
        }

        @Override public String name() { return "read_file"; }
        @Override public String description() { return "Read a UTF-8 text file with numbered lines"; }
        @Override public boolean isReadOnly() { return true; }
        @Override public ToolInputSchema inputSchema() {
            return ToolInputSchema.object(Map.of(
                "path", Map.of("type", "string"),
                "offset", Map.of("type", "integer"),
                "limit", Map.of("type", "integer")
            ));
        }
        @Override public PermissionResult validate(Input input, ToolContext context) {
            if (input.path() == null || input.path().isBlank()) return PermissionResult.deny("Path must not be blank.");
            confinedPath(workspace, input.path());
            return PermissionResult.allow();
        }
        @Override public String execute(Input input, ToolContext context) throws Exception {
            var target = confinedPath(workspace, input.path());
            if (!Files.exists(target)) throw new IOException("File not found: " + target);
            var rawContent = Files.readString(target, StandardCharsets.UTF_8);
            if (rawContent.length() > MAX_FILE_SIZE_BYTES) rawContent = rawContent.substring(0, MAX_FILE_SIZE_BYTES) + System.lineSeparator() + "... [truncated at 100KB]";
            var lines = rawContent.split("\\R", -1);
            int start = Math.max(0, (input.offset() == null ? 1 : input.offset()) - 1);
            int limit = input.limit() == null ? lines.length : Math.max(0, input.limit());
            int end = Math.min(lines.length, start + limit);
            var sb = new StringBuilder();
            for (int i = start; i < end; i++) {
                if (sb.length() > 0) sb.append(System.lineSeparator());
                sb.append("%d: %s".formatted(i + 1, lines[i]));
            }
            return sb.toString();
        }
        record Input(String path, Integer offset, Integer limit) {}
    }

    private static final class ConfinedGlobTool implements ToolDefinition<ConfinedGlobTool.Input, ConfinedGlobTool.Output> {
        private static final int MAX_RESULTS = 500;
        private static final Set<String> EXCLUDED_DIRS = Set.of(".git", ".svn", ".hg", ".bzr", ".jj", ".sl", "node_modules", ".gradle", "target", "__pycache__");
        private final Path workspace;

        private ConfinedGlobTool(Path workspace) {
            this.workspace = workspace;
        }

        @Override public String name() { return "glob"; }
        @Override public String description() { return "Match files under a directory using a glob pattern"; }
        @Override public boolean isReadOnly() { return true; }
        @Override public ToolInputSchema inputSchema() { return ToolInputSchema.object(Map.of("pattern", Map.of("type", "string"), "basePath", Map.of("type", "string"))); }
        @Override public PermissionResult validate(Input input, ToolContext context) {
            if (input.pattern() == null || input.pattern().isBlank()) return PermissionResult.deny("Pattern must not be blank.");
            if (input.basePath() != null && !input.basePath().isBlank()) confinedPath(workspace, input.basePath());
            return PermissionResult.allow();
        }
        @Override public Output execute(Input input, ToolContext context) throws Exception {
            var basePath = input.basePath() == null || input.basePath().isBlank() ? workspace : confinedPath(workspace, input.basePath());
            if (!Files.exists(basePath)) return new Output(List.of(), 0, true);
            var matcher = FileSystems.getDefault().getPathMatcher("glob:" + input.pattern());
            var results = new ArrayList<String>();
            try (Stream<Path> stream = Files.walk(basePath)) {
                stream.filter(Files::isRegularFile)
                    .filter(path -> !isExcluded(path, basePath))
                    .filter(path -> matcher.matches(basePath.relativize(path)) || matcher.matches(path.getFileName()))
                    .sorted(Comparator.naturalOrder())
                    .forEach(path -> { if (results.size() < MAX_RESULTS) results.add(path.toAbsolutePath().normalize().toString()); });
            }
            return new Output(results, results.size(), results.size() >= MAX_RESULTS);
        }
        private boolean isExcluded(Path path, Path basePath) {
            var relative = basePath.relativize(path);
            for (int i = 0; i < relative.getNameCount(); i++) if (EXCLUDED_DIRS.contains(relative.getName(i).toString())) return true;
            return false;
        }
        record Input(String pattern, String basePath) {}
        record Output(List<String> filenames, int numFiles, boolean truncated) {}
    }

    private static final class ConfinedGrepTool implements ToolDefinition<ConfinedGrepTool.Input, ConfinedGrepTool.Output> {
        private static final int DEFAULT_HEAD_LIMIT = 250;
        private static final int MAX_RESULTS = 1000;
        private static final int MAX_LINE_LENGTH = 500;
        private static final Set<String> EXCLUDED_DIRS = Set.of(".git", ".svn", ".hg", ".bzr", ".jj", ".sl", "node_modules", ".gradle", "target", "__pycache__");
        private final Path workspace;

        private ConfinedGrepTool(Path workspace) {
            this.workspace = workspace;
        }

        @Override public String name() { return "grep"; }
        @Override public String description() { return "Search file contents with a regular expression pattern"; }
        @Override public boolean isReadOnly() { return true; }
        @Override public ToolInputSchema inputSchema() {
            return ToolInputSchema.object(Map.of(
                "pattern", Map.of("type", "string"), "basePath", Map.of("type", "string"), "glob", Map.of("type", "string"),
                "output_mode", Map.of("type", "string"), "context", Map.of("type", "integer"), "head_limit", Map.of("type", "integer"),
                "case_insensitive", Map.of("type", "boolean")
            ));
        }
        @Override public PermissionResult validate(Input input, ToolContext context) {
            if (input.pattern() == null || input.pattern().isBlank()) return PermissionResult.deny("Pattern must not be blank.");
            try { Pattern.compile(input.pattern(), Boolean.TRUE.equals(input.caseInsensitive()) ? Pattern.CASE_INSENSITIVE : 0); }
            catch (PatternSyntaxException e) { return PermissionResult.deny("Invalid regex pattern: " + e.getMessage()); }
            if (input.basePath() != null && !input.basePath().isBlank()) confinedPath(workspace, input.basePath());
            return PermissionResult.allow();
        }
        @Override public Output execute(Input input, ToolContext context) throws Exception {
            var basePath = input.basePath() == null || input.basePath().isBlank() ? workspace : confinedPath(workspace, input.basePath());
            if (!Files.exists(basePath)) return new Output("content", 0, List.of(), "Path does not exist: " + basePath, 0, 0, false);
            var pattern = Pattern.compile(input.pattern(), Boolean.TRUE.equals(input.caseInsensitive()) ? Pattern.CASE_INSENSITIVE : 0);
            var mode = input.outputMode() == null ? "files_with_matches" : input.outputMode();
            var limit = input.headLimit() == null ? DEFAULT_HEAD_LIMIT : Math.max(0, input.headLimit());
            var effectiveLimit = limit == 0 ? MAX_RESULTS : Math.min(limit, MAX_RESULTS);
            if ("files_with_matches".equals(mode)) return filesMode(basePath, pattern, input.glob(), effectiveLimit);
            if ("count".equals(mode)) return countMode(basePath, pattern, input.glob(), effectiveLimit);
            return contentMode(basePath, pattern, input.glob(), input.context() == null ? 0 : Math.max(0, input.context()), effectiveLimit);
        }
        private Output filesMode(Path basePath, Pattern pattern, String globPattern, int limit) throws Exception {
            var files = new ArrayList<String>();
            for (var path : candidateFiles(basePath, globPattern)) {
                if (files.size() >= limit) break;
                try { if (pattern.matcher(Files.readString(path, StandardCharsets.UTF_8)).find()) files.add(path.toAbsolutePath().normalize().toString()); } catch (Exception ignored) {}
            }
            return new Output("files_with_matches", files.size(), files, null, 0, 0, files.size() >= limit);
        }
        private Output countMode(Path basePath, Pattern pattern, String globPattern, int limit) throws Exception {
            var lines = new ArrayList<String>();
            int totalMatches = 0;
            for (var path : candidateFiles(basePath, globPattern)) {
                if (lines.size() >= limit) break;
                try {
                    var matcher = pattern.matcher(Files.readString(path, StandardCharsets.UTF_8));
                    int count = 0;
                    while (matcher.find()) count++;
                    if (count > 0) { lines.add(path.toAbsolutePath().normalize() + ":" + count); totalMatches += count; }
                } catch (Exception ignored) {}
            }
            var content = String.join(System.lineSeparator(), lines);
            return new Output("count", lines.size(), List.of(), content.isEmpty() ? null : content, 0, totalMatches, lines.size() >= limit);
        }
        private Output contentMode(Path basePath, Pattern pattern, String globPattern, int contextLines, int limit) throws Exception {
            var resultLines = new ArrayList<String>();
            int totalFiles = 0;
            for (var path : candidateFiles(basePath, globPattern)) {
                if (resultLines.size() >= limit) break;
                try {
                    var allLines = Files.readAllLines(path, StandardCharsets.UTF_8);
                    var indices = new LinkedHashSet<Integer>();
                    for (int i = 0; i < allLines.size(); i++) if (pattern.matcher(allLines.get(i)).find()) for (int j = Math.max(0, i - contextLines); j <= Math.min(allLines.size() - 1, i + contextLines); j++) indices.add(j);
                    if (!indices.isEmpty()) totalFiles++;
                    for (int idx : indices) {
                        if (resultLines.size() >= limit) break;
                        var line = allLines.get(idx);
                        if (line.length() > MAX_LINE_LENGTH) line = line.substring(0, MAX_LINE_LENGTH) + "... [truncated]";
                        resultLines.add("%s:%d:%s".formatted(path.toAbsolutePath().normalize(), idx + 1, line));
                    }
                } catch (Exception ignored) {}
            }
            return new Output("content", totalFiles, List.of(), String.join(System.lineSeparator(), resultLines), resultLines.size(), 0, resultLines.size() >= limit);
        }
        private List<Path> candidateFiles(Path basePath, String globPattern) throws Exception {
            try (Stream<Path> paths = Files.walk(basePath)) {
                return paths.filter(Files::isRegularFile).filter(p -> !isExcluded(p, basePath)).filter(p -> matchesGlob(p, basePath, globPattern)).toList();
            }
        }
        private boolean isExcluded(Path path, Path basePath) {
            var relative = basePath.relativize(path);
            for (int i = 0; i < relative.getNameCount(); i++) if (EXCLUDED_DIRS.contains(relative.getName(i).toString())) return true;
            return false;
        }
        private boolean matchesGlob(Path path, Path basePath, String globPattern) {
            if (globPattern == null || globPattern.isBlank()) return true;
            try {
                var matcher = FileSystems.getDefault().getPathMatcher("glob:" + globPattern);
                var relative = basePath.relativize(path);
                return matcher.matches(relative) || matcher.matches(path.getFileName());
            } catch (Exception e) { return true; }
        }
        record Input(String pattern, String basePath, String glob, String outputMode, Integer context, Integer headLimit, Boolean caseInsensitive) {}
        record Output(String mode, int numFiles, List<String> filenames, String content, int numLines, int numMatches, boolean truncated) {}
    }
}
