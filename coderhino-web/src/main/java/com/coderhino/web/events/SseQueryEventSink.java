package com.coderhino.web.events;

import com.coderhino.query.QueryEventSink;
import com.coderhino.services.summary.FileChangeSummary;
import com.coderhino.services.summary.FileChangeTracker;
import com.coderhino.services.summary.SessionEndSummary;
import com.coderhino.state.BootstrapState;
import com.coderhino.state.SessionStore;
import com.coderhino.web.session.WebSession;

import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

public final class SseQueryEventSink implements QueryEventSink {

    private final String sessionId;
    private final String runId;
    private final SessionEventBus eventBus;
    private final FileChangeTracker fileChangeTracker;
    private final UUID sessionUuid;
    private final BootstrapState bootstrapState;
    private final WebSession webSession;
    private final String projectId;
    private final SessionStore sessionStore;
    private final AtomicReference<PendingQuestionAwaiter> pendingQuestionAwaiter = new AtomicReference<>();
    private final AtomicBoolean cancelled = new AtomicBoolean(false);

    public SseQueryEventSink(String sessionId, String runId, SessionEventBus eventBus) {
        this(sessionId, runId, eventBus, null, null, null, null, null, null);
    }

    public SseQueryEventSink(String sessionId, String runId, SessionEventBus eventBus, FileChangeTracker fileChangeTracker, UUID sessionUuid) {
        this(sessionId, runId, eventBus, fileChangeTracker, sessionUuid, null, null, null, null);
    }

    public SseQueryEventSink(String sessionId, String runId, SessionEventBus eventBus, FileChangeTracker fileChangeTracker, UUID sessionUuid, BootstrapState bootstrapState) {
        this(sessionId, runId, eventBus, fileChangeTracker, sessionUuid, bootstrapState, null, null, null);
    }

    public SseQueryEventSink(String sessionId, String runId, SessionEventBus eventBus, FileChangeTracker fileChangeTracker,
                             UUID sessionUuid, BootstrapState bootstrapState, WebSession webSession) {
        this(sessionId, runId, eventBus, fileChangeTracker, sessionUuid, bootstrapState, webSession, null, null);
    }

    public SseQueryEventSink(String sessionId, String runId, SessionEventBus eventBus, FileChangeTracker fileChangeTracker,
                              UUID sessionUuid, BootstrapState bootstrapState, WebSession webSession, String projectId) {
        this(sessionId, runId, eventBus, fileChangeTracker, sessionUuid, bootstrapState, webSession, projectId, null);
    }

    public SseQueryEventSink(String sessionId, String runId, SessionEventBus eventBus, FileChangeTracker fileChangeTracker,
                             UUID sessionUuid, BootstrapState bootstrapState, WebSession webSession, String projectId,
                             SessionStore sessionStore) {
        this.sessionId = sessionId;
        this.runId = runId;
        this.eventBus = eventBus;
        this.fileChangeTracker = fileChangeTracker;
        this.sessionUuid = sessionUuid;
        this.bootstrapState = bootstrapState;
        this.webSession = webSession;
        this.projectId = projectId;
        this.sessionStore = sessionStore;
    }

    @Override
    public void onTextChunk(String chunk) {
        if (isCancelled()) {
            return;
        }
        var sequence = webSession != null ? webSession.recordReplayTextChunk(runId, chunk) : null;
        eventBus.publish(sessionId, SessionEvent.textChunk(runId, chunk, sequence), runId, sequence);
    }

    @Override
    public void onThinkingDelta(String thinking) {
        if (isCancelled()) {
            return;
        }
        Long sequence = webSession != null ? webSession.recordReplayThinkingDelta(runId, thinking) : null;
        eventBus.publish(sessionId, SessionEvent.thinkingDelta(runId, thinking, sequence), runId, sequence);
    }

    @Override
    public void onToolInputDelta(String toolName, String toolUseId, String partialJson) {
        if (isCancelled()) {
            return;
        }
        Long sequence = webSession != null ? webSession.recordReplayToolInputDelta(runId, toolName, toolUseId, partialJson) : null;
        eventBus.publish(sessionId, SessionEvent.toolInputDelta(runId, toolName, toolUseId, partialJson, sequence), runId, sequence);
    }

    @Override
    public void onStatus(String message) {
        if (isCancelled()) {
            return;
        }
        var sequence = webSession != null ? webSession.recordReplayStatus(runId, message) : null;
        eventBus.publish(sessionId, SessionEvent.status(runId, message, sequence), runId, sequence);
    }

    @Override
    public void onToolCall(String toolName, String toolUseId, String argumentsJson) {
        if (isCancelled()) {
            return;
        }
        var sequence = webSession != null ? webSession.recordReplayToolCall(runId, toolName, toolUseId, argumentsJson) : null;
        eventBus.publish(sessionId, SessionEvent.toolCall(runId, toolName, toolUseId, argumentsJson, sequence), runId, sequence);
    }

    @Override
    public void onToolResult(String toolName, String toolUseId, String result) {
        if (isCancelled()) {
            return;
        }
        var sequence = webSession != null ? webSession.recordReplayToolResult(runId, toolName, toolUseId, result) : null;
        eventBus.publish(sessionId, SessionEvent.toolResult(runId, toolName, toolUseId, result, sequence), runId, sequence);
    }

    @Override
    public String onAskUserQuestion(String toolUseId, String question, java.util.List<String> choices) {
        if (webSession == null) {
            return null;
        }
        if (isCancelled()) {
            return null;
        }
        var normalizedChoices = choices == null ? java.util.List.<String>of() : java.util.List.copyOf(choices);
        var sequence = webSession.recordReplayPendingQuestion(runId, toolUseId, question, normalizedChoices);
        webSession.setCurrentRunStatus(com.coderhino.web.dto.RunDto.RunStatus.WAITING_FOR_USER);
        eventBus.publish(sessionId, SessionEvent.askUserQuestion(runId, toolUseId, question, normalizedChoices, sequence), runId, sequence);

        var awaiter = new PendingQuestionAwaiter(toolUseId);
        pendingQuestionAwaiter.set(awaiter);
        try {
            if (isCancelled()) {
                awaiter.cancel();
            }
            awaiter.await();
            if (awaiter.wasCancelled()) {
                webSession.clearPendingQuestion(runId);
                return null;
            }
            webSession.clearPendingQuestion(runId);
            webSession.setCurrentRunStatus(com.coderhino.web.dto.RunDto.RunStatus.RUNNING);
            return awaiter.answer();
        } finally {
            pendingQuestionAwaiter.compareAndSet(awaiter, null);
        }
    }

    public boolean answerPendingQuestion(String toolUseId, String answer) {
        if (isCancelled()) {
            return false;
        }
        var awaiter = pendingQuestionAwaiter.get();
        if (awaiter == null || !awaiter.matches(toolUseId)) {
            return false;
        }
        awaiter.answer(answer);
        return true;
    }

    public boolean hasPendingQuestion(String toolUseId) {
        var awaiter = pendingQuestionAwaiter.get();
        return awaiter != null && awaiter.matches(toolUseId);
    }

    public boolean cancel() {
        var changed = cancelled.compareAndSet(false, true);
        var awaiter = pendingQuestionAwaiter.getAndSet(null);
        if (awaiter != null) {
            awaiter.cancel();
        }
        if (webSession != null) {
            webSession.clearPendingQuestion(runId);
        }
        return changed;
    }

    public boolean isCancelled() {
        return cancelled.get();
    }

    @Override
    public void onUsage(long inputTokens, long outputTokens, long cacheCreationTokens, long cacheReadTokens) {
        if (isCancelled()) {
            return;
        }
        long toolUses = 0;
        long contextLength = 0;
        if (bootstrapState != null) {
            var state = bootstrapState.get();
            if (state.currentUsage() != null) {
                toolUses = state.currentUsage().toolUses();
                contextLength = state.currentUsage().contextLength();
            }
        }
        var sequence = webSession != null
            ? webSession.recordReplayUsage(runId, inputTokens, outputTokens, cacheCreationTokens, cacheReadTokens, toolUses, contextLength)
            : null;
        eventBus.publish(sessionId, SessionEvent.usage(runId, inputTokens, outputTokens, cacheCreationTokens, cacheReadTokens, toolUses, contextLength, sequence), runId, sequence);
    }

    @Override
    public void onError(String error) {
        if (isCancelled()) {
            return;
        }
        var sequence = webSession != null ? webSession.recordReplayFailed(runId, error) : null;
        eventBus.publish(sessionId, SessionEvent.failed(runId, error, sequence), runId, sequence);
    }

    @Override
    public void onCompleted(String finalText) {
        if (isCancelled()) {
            return;
        }
        var fileSummary = buildFileChangeSummary();
        var sequence = webSession != null ? webSession.recordReplayCompleted(runId, finalText, fileSummary) : null;
        persistCompletedTurnActivityBeforeEvent();
        if (fileSummary != null) {
            eventBus.publish(sessionId, SessionEvent.completed(runId, finalText, fileSummary, sequence, projectId, sessionId), runId, sequence);
        } else {
            eventBus.publish(sessionId, SessionEvent.completed(runId, finalText, null, sequence, projectId, sessionId), runId, sequence);
        }
    }

    private void persistCompletedTurnActivityBeforeEvent() {
        if (webSession == null || sessionStore == null || bootstrapState == null) {
            return;
        }
        var completedTurnActivity = webSession.snapshotCompletedTurnActivity();
        if (completedTurnActivity == null) {
            return;
        }
        bootstrapState.update(state -> state.withSessionRuntime(
            state.sessionRuntime().appendCompletedTurnActivity(completedTurnActivity)
        ));
        sessionStore.appendCompletedTurnActivity(bootstrapState.get(), completedTurnActivity);
    }

    private SessionEvent.FileChangeSummaryPayload buildFileChangeSummary() {
        if (fileChangeTracker == null || sessionUuid == null) return null;
        var summary = new SessionEndSummary(fileChangeTracker).buildSummary(sessionUuid);
        if (summary.totalChanges() == 0) return null;
        return new SessionEvent.FileChangeSummaryPayload(
            summary.totalChanges(),
            summary.created().stream().map(Object::toString).collect(Collectors.toList()),
            summary.modified().stream().map(Object::toString).collect(Collectors.toList()),
            summary.deleted().stream().map(Object::toString).collect(Collectors.toList())
        );
    }

    private static final class PendingQuestionAwaiter {
        private final String toolUseId;
        private final CountDownLatch latch = new CountDownLatch(1);
        private volatile String answer;
        private volatile boolean cancelled;

        private PendingQuestionAwaiter(String toolUseId) {
            this.toolUseId = toolUseId;
        }

        boolean matches(String candidateToolUseId) {
            return java.util.Objects.equals(toolUseId, candidateToolUseId);
        }

        void answer(String answer) {
            this.answer = answer;
            this.cancelled = false;
            latch.countDown();
        }

        void cancel() {
            this.answer = null;
            this.cancelled = true;
            latch.countDown();
        }

        void await() {
            try {
                while (!latch.await(30, TimeUnit.SECONDS)) {
                    // Loop so interrupt status can still be handled and the thread can keep waiting.
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                answer = null;
                cancelled = true;
            }
        }

        String answer() {
            return answer == null ? "" : answer;
        }

        boolean wasCancelled() {
            return cancelled;
        }
    }
}
