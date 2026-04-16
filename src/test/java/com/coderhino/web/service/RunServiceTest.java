package com.coderhino.web.service;

import com.coderhino.web.approval.ApprovalService;
import com.coderhino.state.SessionStore;
import com.coderhino.types.PermissionMode;
import com.coderhino.web.dto.MessageSubmitRequest;
import com.coderhino.web.dto.PendingQuestionAnswerRequest;
import com.coderhino.web.dto.RunDto;
import com.coderhino.web.events.SessionEvent;
import com.coderhino.web.events.SessionEventBus;
import com.coderhino.web.exception.RunNotFoundException;
import com.coderhino.web.exception.SessionBusyException;
import com.coderhino.web.session.WebSession;
import com.coderhino.web.session.WebSessionRegistry;
import com.coderhino.web.settings.SettingsPersistenceService;
import com.coderhino.web.settings.WebSettings;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RunServiceTest {

    @Test
    void submitRunStartsImmediateRunInBypassMode() throws Exception {
        var service = createRunService();
        var session = WebSession.create("ses-1");

        var result = service.submitRun(session, "hello");

        assertEquals(RunDto.RunStatus.RUNNING, result.getStatus());
        assertNotNull(result.getRunId());
        assertNull(result.getApprovalId());
        assertEquals("hello", result.getVisiblePrompt());
        assertEquals(result.getRunId(), service.executionService.lastExecutedRunId);
        assertEquals("hello", service.executionService.lastExecutedInput);
        assertEquals("hello", service.executionService.lastExecutedVisiblePrompt);
        assertEquals(RunDto.RunStatus.RUNNING, session.getCurrentRunStatus());
        assertTrue(session.getActiveRun().get());
        assertNotNull(session.getActiveRunReplaySnapshot());
        assertEquals(result.getRunId(), session.getActiveRunReplaySnapshot().runId());
        assertEquals(0, service.eventBus.getReplayEventCount(session.getSessionId()));
    }

    @Test
    void submitRunRejectsBusySession() throws Exception {
        var service = createRunService();
        var session = WebSession.create("ses-2");
        session.getActiveRun().set(true);
        session.setActiveRunId("run-busy");

        var exception = assertThrows(SessionBusyException.class, () -> service.submitRun(session, "hello"));

        assertEquals("run-busy", exception.getActiveRunId());
    }

    @Test
    void submitRunCreatesPendingApprovalWhenBypassDisabled() throws Exception {
        var settingsService = settingsServiceWithMode("AUTO");
        var eventBus = new SessionEventBus(new ObjectMapper());
        var approvalService = new ApprovalService(eventBus);
        var runExecutionService = new TestRunExecutionService();
        var service = new TestRunService(eventBus, approvalService, settingsService, runExecutionService);
        var session = WebSession.create("ses-3");

        var result = service.submitRun(session, "please approve");

        assertEquals(RunDto.RunStatus.PENDING_APPROVAL, result.getStatus());
        assertNotNull(result.getApprovalId());
        assertEquals(result.getRunId(), session.getActiveRunId());
        assertEquals(RunDto.RunStatus.PENDING_APPROVAL, session.getCurrentRunStatus());
        assertTrue(service.hasDeferredRun(result.getRunId()));
        assertTrue(service.hasPendingApproval(session.getSessionId()));
        assertEquals("please approve", result.getVisiblePrompt());
    }

    @Test
    void denyWithoutExecutionCancelsDeferredRunAndClearsSession() throws Exception {
        var settingsService = settingsServiceWithMode("AUTO");
        var eventBus = new CapturingSessionEventBus();
        var approvalService = new ApprovalService(eventBus);
        var runExecutionService = new TestRunExecutionService();
        var service = new TestRunService(eventBus, approvalService, settingsService, runExecutionService);
        var session = WebSession.create("ses-4");

        var submitResult = service.submitRun(session, "please approve");
        var denied = service.denyWithoutExecution(submitResult.getApprovalId());

        assertEquals(RunDto.RunStatus.CANCELLED, denied.getStatus());
        assertFalse(session.getActiveRun().get());
        assertNull(session.getActiveRunId());
        assertEquals(RunDto.RunStatus.CANCELLED, session.getCurrentRunStatus());
        assertNotNull(eventBus.lastEvent);
        assertEquals(SessionEvent.EventType.cancelled, eventBus.lastEvent.type());
        assertNull(runExecutionService.lastCancelledRunId);
    }

    @Test
    void cancelRunCancelsActiveRunAndPublishesEvent() throws Exception {
        var eventBus = new CapturingSessionEventBus();
        var runExecutionService = new TestRunExecutionService();
        var service = new TestRunService(eventBus, new ApprovalService(eventBus), settingsServiceWithMode("BYPASS"), runExecutionService);
        var session = WebSession.create("ses-5");
        session.getActiveRun().set(true);
        session.setActiveRunId("run-5");
        session.setCurrentRunStatus(RunDto.RunStatus.RUNNING);

        var result = service.cancelRun(session, "run-5");

        assertEquals(RunDto.RunStatus.CANCELLED, result.getStatus());
        assertFalse(session.getActiveRun().get());
        assertNull(session.getActiveRunId());
        assertEquals(RunDto.RunStatus.CANCELLED, session.getCurrentRunStatus());
        assertNotNull(eventBus.lastEvent);
        assertEquals(SessionEvent.EventType.cancelled, eventBus.lastEvent.type());
        assertEquals("run-5", runExecutionService.lastCancelledRunId);
    }

    @Test
    void cancelRunRejectsUnknownRun() throws Exception {
        var service = createRunService();
        var session = WebSession.create("ses-6");
        session.getActiveRun().set(true);
        session.setActiveRunId("run-6");

        assertThrows(RunNotFoundException.class, () -> service.cancelRun(session, "different-run"));
    }

    @Test
    void submitRunAppliesComposerSelectionsBeforeExecution() throws Exception {
        var settingsService = settingsServiceWithMode("AUTO");
        var eventBus = new SessionEventBus(new ObjectMapper());
        var approvalService = new ApprovalService(eventBus);
        var runExecutionService = new TestRunExecutionService();
        var service = new TestRunService(eventBus, approvalService, settingsService, runExecutionService);
        var session = WebSession.create("ses-compose");
        var request = new MessageSubmitRequest("hello");
        request.setModel("GPT-5.4");
        request.setBuildMode(false);
        request.setPlanMode(true);
        request.setModelMode("think");
        request.setVisiblePrompt("expanded visible hello");

        var result = service.submitRun(session, "hello", request);

        assertEquals(RunDto.RunStatus.PENDING_APPROVAL, result.getStatus());
        assertEquals("GPT-5.4", session.getAppState().model());
        assertEquals(PermissionMode.PLAN, session.getAppState().permissionMode());
        assertEquals("think", session.getModelMode());
        assertEquals("expanded visible hello", result.getVisiblePrompt());
    }

    @Test
    void answerPendingQuestionResumesActiveRun() throws Exception {
        var service = createRunService();
        var session = WebSession.create("ses-question");
        session.getActiveRun().set(true);
        session.setActiveRunId("run-question");
        session.setCurrentRunStatus(RunDto.RunStatus.WAITING_FOR_USER);
        session.startActiveRunReplay("run-question");
        session.recordReplayPendingQuestion("run-question", "tool-q-1", "Which file?", java.util.List.of("A", "B"));
        service.executionService.pendingQuestionPresent = true;

        var request = new PendingQuestionAnswerRequest();
        request.setToolUseId("tool-q-1");
        request.setAnswer("B");

        var result = service.answerPendingQuestion(session, "run-question", request);

        assertEquals(RunDto.RunStatus.RUNNING, result.getStatus());
        assertEquals(RunDto.RunStatus.RUNNING, session.getCurrentRunStatus());
        assertNull(session.getPendingQuestion());
        assertEquals("run-question", service.executionService.lastAnsweredRunId);
        assertEquals("tool-q-1", service.executionService.lastAnsweredToolUseId);
        assertEquals("B", service.executionService.lastAnsweredValue);
    }

    private static TestRunService createRunService() throws Exception {
        var eventBus = new SessionEventBus(new ObjectMapper());
        var approvalService = new ApprovalService(eventBus);
        var settingsService = settingsServiceWithMode("BYPASS");
        var runExecutionService = new TestRunExecutionService();
        return new TestRunService(eventBus, approvalService, settingsService, runExecutionService);
    }

    private static SettingsPersistenceService settingsServiceWithMode(String mode) throws Exception {
        var file = Files.createTempFile("web-settings", ".json");
        var service = new SettingsPersistenceService(file);
        var settings = new WebSettings();
        settings.setDefaultPermissionMode(mode);
        service.save(settings);
        return service;
    }

    private static final class TestRunService extends RunService {
        final TestRunExecutionService executionService;
        final SessionEventBus eventBus;

        private TestRunService(SessionEventBus eventBus, ApprovalService approvalService,
                               SettingsPersistenceService settingsService, TestRunExecutionService executionService) {
            super(eventBus, approvalService, settingsService, executionService, null);
            this.executionService = executionService;
            this.eventBus = eventBus;
        }
    }

    private static final class TestRunExecutionService extends RunExecutionService {
        String lastExecutedRunId;
        String lastExecutedInput;
        String lastExecutedVisiblePrompt;
        String lastAnsweredRunId;
        String lastAnsweredToolUseId;
        String lastAnsweredValue;
        String lastCancelledRunId;
        boolean pendingQuestionPresent;

        private TestRunExecutionService() {
            super(new SessionEventBus(new ObjectMapper()), createStubRegistry());
        }

        private static WebSessionRegistry createStubRegistry() {
            return new WebSessionRegistry(
                new com.coderhino.web.session.SessionPersistenceService(
                    java.nio.file.Path.of(System.getProperty("java.io.tmpdir"), "test-sessions-run-" + System.currentTimeMillis())
                ),
                new SessionStore(
                    new ObjectMapper(),
                    java.nio.file.Path.of(System.getProperty("java.io.tmpdir"), "test-project-run-" + System.currentTimeMillis())
                ),
                new SessionEventBus(new ObjectMapper()),
                new com.coderhino.web.project.ProjectPersistenceService() {
                    @Override
                    public java.util.Optional<com.coderhino.web.project.Project> find(String id) {
                        return java.util.Optional.empty();
                    }
                }
            );
        }

        @Override
        public void executeAsync(com.coderhino.web.session.WebSession session, String runId, String input) {
            this.lastExecutedRunId = runId;
            this.lastExecutedInput = input;
            this.lastExecutedVisiblePrompt = input;
        }

        @Override
        public void executeAsync(com.coderhino.web.session.WebSession session, String runId, String input, String visiblePrompt) {
            this.lastExecutedRunId = runId;
            this.lastExecutedInput = input;
            this.lastExecutedVisiblePrompt = visiblePrompt;
        }

        @Override
        public boolean answerPendingQuestion(String runId, String toolUseId, String answer) {
            this.lastAnsweredRunId = runId;
            this.lastAnsweredToolUseId = toolUseId;
            this.lastAnsweredValue = answer;
            return pendingQuestionPresent;
        }

        @Override
        public boolean cancelRun(String runId) {
            this.lastCancelledRunId = runId;
            return true;
        }
    }

    private static final class CapturingSessionEventBus extends SessionEventBus {
        private SessionEvent lastEvent;

        private CapturingSessionEventBus() {
            super(new ObjectMapper());
        }

        @Override
        public void publish(String sessionId, SessionEvent event) {
            this.lastEvent = event;
        }

        @Override
        public void publish(String sessionId, SessionEvent event, String runId, Long sequence) {
            this.lastEvent = event;
        }
    }
}
