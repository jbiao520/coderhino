package com.coderhino.web.session;

import com.coderhino.state.AppState;
import com.coderhino.state.BootstrapState;
import com.coderhino.state.SessionStore;
import com.coderhino.state.SessionRuntime;
import com.coderhino.types.Message;
import com.coderhino.types.PermissionMode;
import com.coderhino.web.events.SessionEvent;
import com.coderhino.web.dto.RunDto;

import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Domain object representing one web browser session.
 * Each web session owns its own {@link BootstrapState} instance — NEVER shared across sessions.
 */
public final class WebSession {

    private final String sessionId;
    private final Instant createdAt;
    private final BootstrapState bootstrapState;

    /**
     * Per-session execution mutex for Task 5 (run execution).
     * Guards against concurrent executions within the same session.
     */
    private final ReentrantLock executionLock = new ReentrantLock();

    /**
     * Tracks whether a run is currently active in this session.
     */
    private final AtomicBoolean activeRun = new AtomicBoolean(false);

    private volatile String activeRunId = null;

    private volatile RunDto.RunStatus currentRunStatus = null;

    private volatile String name;

    private volatile String branch;

    private volatile String providerId;

    private volatile String modelMode = "default";

    private volatile PermissionMode normalPermissionMode = PermissionMode.BYPASS;

    private Runnable persistenceSubscription;

    private final ActiveRunReplay activeRunReplay = new ActiveRunReplay();

    public WebSession(String sessionId, Instant createdAt, BootstrapState bootstrapState) {
        this.sessionId = sessionId;
        this.createdAt = createdAt;
        this.bootstrapState = bootstrapState;
    }

    /**
     * Creates a new WebSession with a fresh {@link BootstrapState}.
     * Uses {@link PermissionMode#BYPASS} as the default for web sessions.
     * Uses the JVM default working directory as cwd.
     */
    public static WebSession create(String sessionId) {
        return create(sessionId, Path.of("").toAbsolutePath().normalize());
    }

    /**
     * Creates a new WebSession with a fresh {@link BootstrapState}.
     * Uses {@link PermissionMode#BYPASS} as the default for web sessions.
     * Uses the provided {@code cwd} as the session working directory, falling back to JVM default if null.
     */
    public static WebSession create(String sessionId, Path cwd) {
        return create(sessionId, cwd, new SessionRuntime(parseSessionUuid(sessionId), null, null, java.util.List.of(), java.util.List.of(), java.util.List.of()));
    }

    public static WebSession create(String sessionId, Path cwd, SessionRuntime sessionRuntime) {
        var effectiveCwd = cwd != null
            ? cwd.toAbsolutePath().normalize().toString()
            : Path.of("").toAbsolutePath().normalize().toString();
        var effectiveRuntime = sessionRuntime != null
            ? sessionRuntime
            : new SessionRuntime(parseSessionUuid(sessionId), null, null, java.util.List.of(), java.util.List.of(), java.util.List.of());
        var initialState = new AppState(
            false,
            null,
            effectiveCwd,
            false,
            true,
            PermissionMode.BYPASS,
            0.0,
            effectiveRuntime,
            effectiveRuntime.transcript().stream()
                .map(com.coderhino.types.Message.Envelope::message)
                .toList()
        );
        var bootstrapState = new BootstrapState(initialState);
        return new WebSession(sessionId, Instant.now(), bootstrapState);
    }

    public void attachPersistence(SessionStore sessionStore) {
        if (sessionStore == null || persistenceSubscription != null) {
            return;
        }
        persistenceSubscription = bootstrapState.onChange(state -> {
            var runtime = state.sessionRuntime();
            var transcript = runtime.transcript();
            if (state.messages().size() <= transcript.size()) {
                return;
            }
            for (int i = transcript.size(); i < state.messages().size(); i++) {
                var message = state.messages().get(i);
                var envelope = sessionStore.recordMessage(state, message);
                var nextState = bootstrapState.get();
                bootstrapState.update(current -> current.withSessionRuntime(nextState.sessionRuntime().append(envelope)));
            }
        });
    }

    public String getSessionId() {
        return sessionId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public BootstrapState getBootstrapState() {
        return bootstrapState;
    }

    public AppState getAppState() {
        return bootstrapState.get();
    }

    public ReentrantLock getExecutionLock() {
        return executionLock;
    }

    public AtomicBoolean getActiveRun() {
        return activeRun;
    }

    public String getActiveRunId() {
        return activeRunId;
    }

    public void setActiveRunId(String activeRunId) {
        this.activeRunId = activeRunId;
    }

    public RunDto.RunStatus getCurrentRunStatus() {
        return currentRunStatus;
    }

    public void setCurrentRunStatus(RunDto.RunStatus currentRunStatus) {
        this.currentRunStatus = currentRunStatus;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getBranch() {
        return branch;
    }

    public void setBranch(String branch) {
        this.branch = branch;
    }

    public String getProviderId() {
        return providerId;
    }

    public void setProviderId(String providerId) {
        this.providerId = providerId == null || providerId.isBlank() ? null : providerId.trim();
    }

    public String getModelMode() {
        return modelMode;
    }

    public void setModelMode(String modelMode) {
        this.modelMode = modelMode == null || modelMode.isBlank() ? "default" : modelMode.trim().toLowerCase();
    }

    public PermissionMode getNormalPermissionMode() {
        return normalPermissionMode;
    }

    public void setNormalPermissionMode(PermissionMode normalPermissionMode) {
        if (normalPermissionMode != null && normalPermissionMode != PermissionMode.PLAN) {
            this.normalPermissionMode = normalPermissionMode;
        }
    }

    public int getMessageCount() {
        return bootstrapState.get().messages().size();
    }

    public void startActiveRunReplay(String runId) {
        activeRunReplay.start(runId);
    }

    public long recordReplayStatus(String runId, String status) {
        return activeRunReplay.recordStatus(runId, status);
    }

    public long recordReplayTextChunk(String runId, String chunk) {
        return activeRunReplay.recordTextChunk(runId, chunk);
    }

    public long recordReplayThinkingDelta(String runId, String thinking) {
        return activeRunReplay.recordThinkingDelta(runId, thinking);
    }

    public long recordReplayToolInputDelta(String runId, String toolName, String toolUseId, String partialJson) {
        return activeRunReplay.recordToolInputDelta(runId, toolName, toolUseId, partialJson);
    }

    public long recordReplayToolCall(String runId, String toolName, String toolUseId, String argumentsJson) {
        return activeRunReplay.recordToolCall(runId, toolName, toolUseId, argumentsJson);
    }

    public long recordReplayToolResult(String runId, String toolName, String toolUseId, String result) {
        return activeRunReplay.recordToolResult(runId, toolName, toolUseId, result);
    }

    public long recordReplayPendingQuestion(String runId, String toolUseId, String question, List<String> choices) {
        return activeRunReplay.recordPendingQuestion(runId, toolUseId, question, choices);
    }

    public void clearPendingQuestion(String runId) {
        activeRunReplay.clearPendingQuestion(runId);
    }

    public PendingQuestionSnapshot getPendingQuestion() {
        var snapshot = activeRunReplay.snapshot();
        return snapshot == null ? null : snapshot.pendingQuestion();
    }

    public long recordReplayUsage(String runId, long inputTokens, long outputTokens,
                                  long cacheCreationTokens, long cacheReadTokens,
                                  long toolUses, long contextLength) {
        return activeRunReplay.recordUsage(runId, inputTokens, outputTokens, cacheCreationTokens,
            cacheReadTokens, toolUses, contextLength);
    }

    public long recordReplayCompleted(String runId, String finalText, SessionEvent.FileChangeSummaryPayload fileSummary) {
        return activeRunReplay.recordCompleted(runId, finalText, fileSummary);
    }

    public long recordReplayFailed(String runId, String error) {
        return activeRunReplay.recordFailed(runId, error);
    }

    public long recordReplayCancelled(String runId) {
        return activeRunReplay.recordCancelled(runId);
    }

    public ActiveRunReplaySnapshot getActiveRunReplaySnapshot() {
        return activeRunReplay.snapshot();
    }

    public void clearActiveRunReplay() {
        activeRunReplay.clear();
    }

    public SessionRuntime.CompletedTurnActivity snapshotCompletedTurnActivity() {
        var replay = activeRunReplay.snapshot();
        if (replay == null) {
            return null;
        }
        var activityTranscript = replay.transcript().stream()
            .filter(item -> !"assistant".equals(item.kind()))
            .map(item -> new SessionRuntime.CompletedTurnActivity.ActivityItem(
                item.kind(),
                item.content(),
                item.toolName(),
                item.toolUseId(),
                item.argumentsJson(),
                item.output()
            ))
            .toList();
        if (activityTranscript.isEmpty() && replay.fileSummary() == null) {
            return null;
        }
        var transcript = bootstrapState.get().sessionRuntime().transcript();
        Message.Envelope assistantEnvelope = null;
        for (int i = transcript.size() - 1; i >= 0; i--) {
            var candidate = transcript.get(i);
            if (candidate.message() instanceof com.coderhino.types.Message.AssistantMessage assistantMessage
                && java.util.Objects.equals(assistantMessage.content(), replay.finalText())) {
                assistantEnvelope = candidate;
                break;
            }
        }
        if (assistantEnvelope == null) {
            return null;
        }
        var fileSummary = replay.fileSummary() == null ? null : new SessionRuntime.CompletedTurnActivity.FileChangeSummary(
            replay.fileSummary().totalChanges(),
            replay.fileSummary().created(),
            replay.fileSummary().modified(),
            replay.fileSummary().deleted()
        );
        return new SessionRuntime.CompletedTurnActivity(assistantEnvelope.uuid(), activityTranscript, fileSummary);
    }

    private static UUID parseSessionUuid(String sessionId) {
        try {
            return UUID.fromString(sessionId);
        } catch (IllegalArgumentException e) {
            return UUID.nameUUIDFromBytes(sessionId.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        }
    }

    @Override
    public String toString() {
        return "WebSession{sessionId='" + sessionId + "', createdAt=" + createdAt +
               ", messages=" + getMessageCount() + '}';
    }

    public record ActiveRunReplaySnapshot(
        String runId,
        List<ReplayTranscriptItemSnapshot> transcript,
        ReplayUsageSnapshot usage,
        Long lastSequence,
        String terminalStatus,
        String finalText,
        String error,
        SessionEvent.FileChangeSummaryPayload fileSummary,
        PendingQuestionSnapshot pendingQuestion
    ) {}

    public record ReplayTranscriptItemSnapshot(
        String kind,
        String content,
        String toolName,
        String toolUseId,
        String argumentsJson,
        String output
    ) {}

    public record ReplayUsageSnapshot(
        long inputTokens,
        long outputTokens,
        long cacheCreationTokens,
        long cacheReadTokens,
        long toolUses,
        long contextLength
    ) {}

    public record PendingQuestionSnapshot(
        String toolUseId,
        String question,
        List<String> choices
    ) {}

    private static final class ActiveRunReplay {
        private String runId;
        private final List<MutableReplayTranscriptItem> transcript = new ArrayList<>();
        private ReplayUsageSnapshot usage;
        private Long lastSequence;
        private String terminalStatus;
        private String finalText;
        private String error;
        private SessionEvent.FileChangeSummaryPayload fileSummary;
        private PendingQuestionSnapshot pendingQuestion;

        synchronized void start(String runId) {
            this.runId = runId;
            transcript.clear();
            usage = null;
            lastSequence = null;
            terminalStatus = null;
            finalText = null;
            error = null;
            fileSummary = null;
            pendingQuestion = null;
        }

        synchronized long recordStatus(String runId, String status) {
            var sequence = nextSequence(runId);
            if (status != null && status.startsWith(com.coderhino.query.ModelStreamEventSink.RETRY_STATUS_PREFIX)) {
                transcript.add(MutableReplayTranscriptItem.status(status));
                return sequence;
            }
            terminalStatus = status;
            return sequence;
        }

        synchronized long recordTextChunk(String runId, String chunk) {
            var sequence = nextSequence(runId);
            if (chunk == null || chunk.isEmpty()) {
                return sequence;
            }
            var lastItem = transcript.isEmpty() ? null : transcript.get(transcript.size() - 1);
            if (lastItem != null && "assistant".equals(lastItem.kind)) {
                lastItem.content = lastItem.content + chunk;
            } else {
                transcript.add(MutableReplayTranscriptItem.assistant(chunk));
            }
            return sequence;
        }

        synchronized long recordThinkingDelta(String runId, String thinking) {
            var sequence = nextSequence(runId);
            if (thinking == null || thinking.isEmpty()) {
                return sequence;
            }
            var lastItem = transcript.isEmpty() ? null : transcript.get(transcript.size() - 1);
            if (lastItem != null && "thinking".equals(lastItem.kind)) {
                lastItem.content = (lastItem.content == null ? "" : lastItem.content) + thinking;
            } else {
                transcript.add(MutableReplayTranscriptItem.thinking(thinking));
            }
            return sequence;
        }

        synchronized long recordToolInputDelta(String runId, String toolName, String toolUseId, String partialJson) {
            var sequence = nextSequence(runId);
            if (partialJson == null || partialJson.isEmpty()) {
                return sequence;
            }
            var lastItem = transcript.isEmpty() ? null : transcript.get(transcript.size() - 1);
            if (lastItem != null
                && "tool-input".equals(lastItem.kind)
                && Objects.equals(lastItem.toolName, toolName)
                && Objects.equals(lastItem.toolUseId, toolUseId)) {
                lastItem.argumentsJson = (lastItem.argumentsJson == null ? "" : lastItem.argumentsJson) + partialJson;
            } else {
                transcript.add(MutableReplayTranscriptItem.toolInput(toolName, toolUseId, partialJson));
            }
            return sequence;
        }

        synchronized long recordToolCall(String runId, String toolName, String toolUseId, String argumentsJson) {
            var sequence = nextSequence(runId);
            transcript.add(MutableReplayTranscriptItem.tool(toolName, toolUseId, argumentsJson));
            return sequence;
        }

        synchronized long recordToolResult(String runId, String toolName, String toolUseId, String result) {
            var sequence = nextSequence(runId);
            pendingQuestion = null;
            for (var item : transcript) {
                if (!"tool".equals(item.kind) || item.output != null) {
                    continue;
                }
                if (toolUseId != null && !toolUseId.isBlank()) {
                    if (Objects.equals(toolUseId, item.toolUseId)) {
                        item.output = result;
                        return sequence;
                    }
                    continue;
                }
                if (Objects.equals(toolName, item.toolName)) {
                    item.output = result;
                    return sequence;
                }
            }
            var unmatched = MutableReplayTranscriptItem.tool(toolName, toolUseId, null);
            unmatched.output = result;
            transcript.add(unmatched);
            return sequence;
        }

        synchronized long recordUsage(String runId, long inputTokens, long outputTokens,
                                      long cacheCreationTokens, long cacheReadTokens,
                                      long toolUses, long contextLength) {
            var sequence = nextSequence(runId);
            usage = new ReplayUsageSnapshot(inputTokens, outputTokens, cacheCreationTokens, cacheReadTokens, toolUses, contextLength);
            return sequence;
        }

        synchronized long recordPendingQuestion(String runId, String toolUseId, String question, List<String> choices) {
            var sequence = nextSequence(runId);
            pendingQuestion = new PendingQuestionSnapshot(toolUseId, question, choices == null ? List.of() : List.copyOf(choices));
            terminalStatus = null;
            return sequence;
        }

        synchronized void clearPendingQuestion(String runId) {
            if (!Objects.equals(this.runId, runId)) {
                return;
            }
            pendingQuestion = null;
        }

        synchronized long recordCompleted(String runId, String finalText, SessionEvent.FileChangeSummaryPayload fileSummary) {
            var sequence = nextSequence(runId);
            terminalStatus = "COMPLETED";
            this.finalText = finalText;
            this.error = null;
            this.fileSummary = fileSummary;
            this.pendingQuestion = null;
            return sequence;
        }

        synchronized long recordFailed(String runId, String error) {
            var sequence = nextSequence(runId);
            terminalStatus = "FAILED";
            this.error = error;
            this.finalText = null;
            this.fileSummary = null;
            this.pendingQuestion = null;
            return sequence;
        }

        synchronized long recordCancelled(String runId) {
            var sequence = nextSequence(runId);
            terminalStatus = "CANCELLED";
            this.error = null;
            this.finalText = null;
            this.fileSummary = null;
            this.pendingQuestion = null;
            return sequence;
        }

        synchronized ActiveRunReplaySnapshot snapshot() {
            if (runId == null) {
                return null;
            }
            return new ActiveRunReplaySnapshot(
                runId,
                transcript.stream().map(MutableReplayTranscriptItem::snapshot).toList(),
                usage,
                lastSequence,
                terminalStatus,
                finalText,
                error,
                fileSummary,
                pendingQuestion
            );
        }

        synchronized void clear() {
            runId = null;
            transcript.clear();
            usage = null;
            lastSequence = null;
            terminalStatus = null;
            finalText = null;
            error = null;
            fileSummary = null;
            pendingQuestion = null;
        }

        private long nextSequence(String nextRunId) {
            if (!Objects.equals(runId, nextRunId)) {
                start(nextRunId);
            }
            var next = lastSequence == null ? 1L : lastSequence + 1L;
            lastSequence = next;
            return next;
        }
    }

    private static final class MutableReplayTranscriptItem {
        private final String kind;
        private String content;
        private String toolName;
        private String toolUseId;
        private String argumentsJson;
        private String output;

        private MutableReplayTranscriptItem(String kind) {
            this.kind = kind;
        }

        static MutableReplayTranscriptItem assistant(String content) {
            var item = new MutableReplayTranscriptItem("assistant");
            item.content = content;
            return item;
        }

        static MutableReplayTranscriptItem thinking(String content) {
            var item = new MutableReplayTranscriptItem("thinking");
            item.content = content;
            return item;
        }

        static MutableReplayTranscriptItem status(String content) {
            var item = new MutableReplayTranscriptItem("status");
            item.content = content;
            return item;
        }

        static MutableReplayTranscriptItem tool(String toolName, String toolUseId, String argumentsJson) {
            var item = new MutableReplayTranscriptItem("tool");
            item.toolName = toolName;
            item.toolUseId = toolUseId;
            item.argumentsJson = argumentsJson;
            return item;
        }

        static MutableReplayTranscriptItem toolInput(String toolName, String toolUseId, String argumentsJson) {
            var item = new MutableReplayTranscriptItem("tool-input");
            item.toolName = toolName;
            item.toolUseId = toolUseId;
            item.argumentsJson = argumentsJson;
            return item;
        }

        ReplayTranscriptItemSnapshot snapshot() {
            return new ReplayTranscriptItemSnapshot(kind, content, toolName, toolUseId, argumentsJson, output);
        }
    }
}
