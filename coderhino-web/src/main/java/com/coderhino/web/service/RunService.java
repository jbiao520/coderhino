package com.coderhino.web.service;

import com.coderhino.types.PermissionMode;
import com.coderhino.web.dto.MessageSubmitRequest;
import com.coderhino.web.dto.PendingQuestionAnswerRequest;
import com.coderhino.web.approval.ApprovalService;
import com.coderhino.web.dto.RunDto;
import com.coderhino.web.events.SessionEvent;
import com.coderhino.web.events.SessionEventBus;
import com.coderhino.web.exception.RunNotFoundException;
import com.coderhino.web.exception.SessionBusyException;
import com.coderhino.web.session.WebSession;
import com.coderhino.web.session.WebSessionRegistry;
import com.coderhino.config.settings.SettingsPersistenceService;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class RunService {

    private final SessionEventBus eventBus;
    private final ApprovalService approvalService;
    private final SettingsPersistenceService settingsService;
    private final RunExecutionService runExecutionService;
    private final WebSessionRegistry sessionRegistry;

    private final Map<String, DeferredRun> deferredRuns = new ConcurrentHashMap<>();
    private final Set<String> sessionsWithPendingApproval = ConcurrentHashMap.newKeySet();

    public static class SubmitResult {
        private final String runId;
        private final RunDto.RunStatus status;
        private final String approvalId;
        private final String visiblePrompt;

        public SubmitResult(String runId, RunDto.RunStatus status, String approvalId, String visiblePrompt) {
            this.runId = runId;
            this.status = status;
            this.approvalId = approvalId;
            this.visiblePrompt = visiblePrompt;
        }

        public String getRunId() { return runId; }
        public RunDto.RunStatus getStatus() { return status; }
        public String getApprovalId() { return approvalId; }
        public String getVisiblePrompt() { return visiblePrompt; }
        public boolean isPendingApproval() { return status == RunDto.RunStatus.PENDING_APPROVAL; }
    }

    public RunService(SessionEventBus eventBus, ApprovalService approvalService,
                      SettingsPersistenceService settingsService, RunExecutionService runExecutionService,
                      WebSessionRegistry sessionRegistry) {
        this.eventBus = eventBus;
        this.approvalService = approvalService;
        this.settingsService = settingsService;
        this.runExecutionService = runExecutionService;
        this.sessionRegistry = sessionRegistry;
    }

    public SubmitResult submitRun(WebSession session, String input) {
        return submitRun(session, input, null);
    }

    public SubmitResult submitRun(WebSession session, String input, MessageSubmitRequest request) {
        if (!session.getActiveRun().compareAndSet(false, true)) {
            throw new SessionBusyException(session.getActiveRunId());
        }

        applyComposerSelections(session, request);
        var visiblePrompt = resolveVisiblePrompt(input, request);

        var mode = session.getAppState().permissionMode().name();
        if (!"BYPASS".equals(mode)) {
            return submitPendingApproval(session, input, visiblePrompt, mode);
        }

        return submitImmediate(session, input, visiblePrompt);
    }

    private SubmitResult submitPendingApproval(WebSession session, String input, String visiblePrompt, String mode) {
        var runId = UUID.randomUUID().toString();
        session.startActiveRunReplay(runId);
        eventBus.resetReplay(session.getSessionId(), runId);
        session.setActiveRunId(runId);
        session.setCurrentRunStatus(RunDto.RunStatus.PENDING_APPROVAL);
        sessionsWithPendingApproval.add(session.getSessionId());

        var record = approvalService.createRequest(
                session.getSessionId(), runId, "run", "Execute: " + truncate(input, 120));
        deferredRuns.put(runId, new DeferredRun(session, runId, input, visiblePrompt));

        return new SubmitResult(runId, RunDto.RunStatus.PENDING_APPROVAL, record.getApprovalId(), visiblePrompt);
    }

    private SubmitResult submitImmediate(WebSession session, String input, String visiblePrompt) {
        var lock = session.getExecutionLock();
        if (!lock.tryLock()) {
            session.getActiveRun().set(false);
            throw new SessionBusyException(session.getActiveRunId());
        }
        try {
            var runId = UUID.randomUUID().toString();
            session.startActiveRunReplay(runId);
            eventBus.resetReplay(session.getSessionId(), runId);
            session.setActiveRunId(runId);
            session.setCurrentRunStatus(RunDto.RunStatus.RUNNING);
            runExecutionService.executeAsync(session, runId, input, visiblePrompt);
            return new SubmitResult(runId, RunDto.RunStatus.RUNNING, null, visiblePrompt);
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    public RunDto approveAndExecute(String approvalId) {
        var record = approvalService.approve(approvalId);

        var deferred = deferredRuns.remove(record.getRunId());
        if (deferred == null) {
            return new RunDto(record.getRunId(), RunDto.RunStatus.RUNNING);
        }

        var session = deferred.session();
        sessionsWithPendingApproval.remove(session.getSessionId());

        var lock = session.getExecutionLock();
        lock.lock();
        try {
            session.startActiveRunReplay(deferred.runId());
            eventBus.resetReplay(session.getSessionId(), deferred.runId());
            session.setCurrentRunStatus(RunDto.RunStatus.RUNNING);
            runExecutionService.executeAsync(session, deferred.runId(), deferred.input(), deferred.visiblePrompt());
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }

        return new RunDto(deferred.runId(), RunDto.RunStatus.RUNNING);
    }

    public RunDto denyWithoutExecution(String approvalId) {
        var record = approvalService.deny(approvalId);

        var deferred = deferredRuns.remove(record.getRunId());
        if (deferred != null) {
            var session = deferred.session();
            sessionsWithPendingApproval.remove(session.getSessionId());
            var sequence = session.recordReplayCancelled(deferred.runId());
            session.setCurrentRunStatus(RunDto.RunStatus.CANCELLED);
            session.getActiveRun().set(false);
            session.setActiveRunId(null);
            if (sessionRegistry != null) {
                sessionRegistry.persistSessionState(session);
            }
            eventBus.publish(session.getSessionId(), SessionEvent.cancelled(deferred.runId(), sequence), deferred.runId(), sequence);
        }

        return new RunDto(record.getRunId(), RunDto.RunStatus.CANCELLED);
    }

    public RunDto cancelRun(WebSession session, String runId) {
        var currentRunId = session.getActiveRunId();
        if (currentRunId == null || !currentRunId.equals(runId)) {
            throw new RunNotFoundException(runId);
        }

        sessionsWithPendingApproval.remove(session.getSessionId());
        runExecutionService.cancelRun(runId);
        var sequence = session.recordReplayCancelled(runId);
        session.setCurrentRunStatus(RunDto.RunStatus.CANCELLED);
        session.getActiveRun().set(false);
        session.setActiveRunId(null);
        if (sessionRegistry != null) {
            sessionRegistry.persistSessionState(session);
        }

        eventBus.publish(session.getSessionId(), SessionEvent.cancelled(runId, sequence), runId, sequence);

        return new RunDto(runId, RunDto.RunStatus.CANCELLED);
    }

    public RunDto answerPendingQuestion(WebSession session, String runId, PendingQuestionAnswerRequest request) {
        var currentRunId = session.getActiveRunId();
        if (currentRunId == null || !currentRunId.equals(runId)) {
            throw new RunNotFoundException(runId);
        }
        if (request == null || request.getToolUseId() == null || request.getToolUseId().isBlank()) {
            throw new IllegalArgumentException("toolUseId is required");
        }
        if (session.getCurrentRunStatus() == RunDto.RunStatus.CANCELLED) {
            throw new IllegalStateException("Run has already been cancelled");
        }
        var answer = request.getAnswer() == null ? "" : request.getAnswer();
        var answered = runExecutionService.answerPendingQuestion(runId, request.getToolUseId(), answer);
        if (!answered) {
            throw new IllegalStateException("No pending question matches that tool use");
        }
        session.clearPendingQuestion(runId);
        session.setCurrentRunStatus(RunDto.RunStatus.RUNNING);
        if (sessionRegistry != null) {
            sessionRegistry.persistSessionState(session);
        }
        return new RunDto(runId, RunDto.RunStatus.RUNNING);
    }

    public Optional<RunDto> getActiveRun(WebSession session) {
        var runId = session.getActiveRunId();
        var status = session.getCurrentRunStatus();
        if (runId == null || status == null) {
            return Optional.empty();
        }
        return Optional.of(new RunDto(runId, status));
    }

    public boolean hasDeferredRun(String runId) {
        return deferredRuns.containsKey(runId);
    }

    public boolean hasPendingApproval(String sessionId) {
        return sessionsWithPendingApproval.contains(sessionId);
    }

    private String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, max) + "...";
    }

    private void applyComposerSelections(WebSession session, MessageSubmitRequest request) {
        if (request == null) {
            var defaultMode = parsePermissionMode(settingsService.load().getDefaultPermissionMode(), PermissionMode.BYPASS);
            session.setNormalPermissionMode(defaultMode == PermissionMode.PLAN ? PermissionMode.BYPASS : defaultMode);
            if (session.getAppState().permissionMode() == PermissionMode.PLAN) {
                session.getBootstrapState().update(state -> state.withPermissionMode(PermissionMode.PLAN));
            } else {
                session.getBootstrapState().update(state -> state.withPermissionMode(session.getNormalPermissionMode()));
            }
            return;
        }

        if (request.getModel() != null && !request.getModel().isBlank()) {
            session.getBootstrapState().update(state -> state.withModel(request.getModel().trim()));
        }

        if (request.getProviderId() != null) {
            session.setProviderId(request.getProviderId());
        }

        if (request.getModelMode() != null) {
            session.setModelMode(request.getModelMode());
        }

        var fallbackMode = session.getNormalPermissionMode();
        if (fallbackMode == PermissionMode.PLAN) {
            fallbackMode = parsePermissionMode(settingsService.load().getDefaultPermissionMode(), PermissionMode.BYPASS);
        }
        if (request.getBuildMode() == null || Boolean.TRUE.equals(request.getBuildMode())) {
            session.setNormalPermissionMode(fallbackMode);
        }

        var nextMode = Boolean.TRUE.equals(request.getPlanMode()) ? PermissionMode.PLAN : session.getNormalPermissionMode();
        session.getBootstrapState().update(state -> state.withPermissionMode(nextMode));
        if (sessionRegistry != null) {
            sessionRegistry.persistSessionState(session);
        }
    }

    private PermissionMode parsePermissionMode(String rawMode, PermissionMode fallback) {
        if (rawMode == null || rawMode.isBlank()) {
            return fallback;
        }
        try {
            var parsed = PermissionMode.valueOf(rawMode.trim().toUpperCase());
            return parsed == PermissionMode.PLAN ? fallback : parsed;
        } catch (IllegalArgumentException e) {
            return fallback;
        }
    }

    private String resolveVisiblePrompt(String input, MessageSubmitRequest request) {
        if (request == null || request.getVisiblePrompt() == null || request.getVisiblePrompt().isBlank()) {
            return input;
        }
        return request.getVisiblePrompt().trim();
    }

    private record DeferredRun(WebSession session, String runId, String input, String visiblePrompt) {}
}
