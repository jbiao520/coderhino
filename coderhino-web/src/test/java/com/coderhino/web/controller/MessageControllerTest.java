package com.coderhino.web.controller;

import com.coderhino.web.dto.PendingQuestionAnswerRequest;
import com.coderhino.web.dto.RunDto;
import com.coderhino.web.service.RunService;
import com.coderhino.web.session.WebSession;
import com.coderhino.web.session.WebSessionRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class MessageControllerTest {

    @Test
    void answerPendingQuestionReturnsOk() {
        var session = WebSession.create("ses-ctrl");
        var registry = new StubRegistry(session);
        var runService = new StubRunService();
        var controller = new MessageController(registry, runService);
        var request = new PendingQuestionAnswerRequest();
        request.setToolUseId("tool-1");
        request.setAnswer("custom");

        var response = controller.answerPendingQuestion("ses-ctrl", "run-1", request);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertInstanceOf(RunDto.class, response.getBody());
        assertEquals("run-1", ((RunDto) response.getBody()).getRunId());
        assertEquals("tool-1", runService.lastToolUseId);
        assertEquals("custom", runService.lastAnswer);
    }

    private static final class StubRunService extends RunService {
        String lastToolUseId;
        String lastAnswer;

        private StubRunService() {
            super(new com.coderhino.web.events.SessionEventBus(new com.fasterxml.jackson.databind.ObjectMapper()),
                new com.coderhino.web.approval.ApprovalService(new com.coderhino.web.events.SessionEventBus(new com.fasterxml.jackson.databind.ObjectMapper())),
                new com.coderhino.config.settings.SettingsPersistenceService(java.nio.file.Path.of(System.getProperty("java.io.tmpdir"), "message-controller-test-settings-" + System.currentTimeMillis() + ".json")),
                new com.coderhino.web.service.RunExecutionService(new com.coderhino.web.events.SessionEventBus(new com.fasterxml.jackson.databind.ObjectMapper()),
                    new com.coderhino.web.session.WebSessionRegistry(
                        new com.coderhino.web.session.SessionPersistenceService(java.nio.file.Path.of(System.getProperty("java.io.tmpdir"), "message-controller-test-session-" + System.currentTimeMillis())),
                        new com.coderhino.state.SessionStore(new com.fasterxml.jackson.databind.ObjectMapper(), java.nio.file.Path.of(System.getProperty("java.io.tmpdir"), "message-controller-test-project-" + System.currentTimeMillis())),
                        new com.coderhino.web.events.SessionEventBus(new com.fasterxml.jackson.databind.ObjectMapper()),
                        new com.coderhino.web.project.ProjectPersistenceService() {
                            @Override
                            public Optional<com.coderhino.web.project.Project> find(String id) {
                                return Optional.empty();
                            }
                        }
                    )),
                null);
        }

        @Override
        public RunDto answerPendingQuestion(WebSession session, String runId, PendingQuestionAnswerRequest request) {
            this.lastToolUseId = request.getToolUseId();
            this.lastAnswer = request.getAnswer();
            return new RunDto(runId, RunDto.RunStatus.RUNNING);
        }
    }

    private static final class StubRegistry extends WebSessionRegistry {
        private final WebSession session;

        private StubRegistry(WebSession session) {
            super(
                new com.coderhino.web.session.SessionPersistenceService(java.nio.file.Path.of(System.getProperty("java.io.tmpdir"), "message-controller-registry-session-" + System.currentTimeMillis())),
                new com.coderhino.state.SessionStore(new com.fasterxml.jackson.databind.ObjectMapper(), java.nio.file.Path.of(System.getProperty("java.io.tmpdir"), "message-controller-registry-project-" + System.currentTimeMillis())),
                new com.coderhino.web.events.SessionEventBus(new com.fasterxml.jackson.databind.ObjectMapper()),
                new com.coderhino.web.project.ProjectPersistenceService() {
                    @Override
                    public Optional<com.coderhino.web.project.Project> find(String id) {
                        return Optional.empty();
                    }
                }
            );
            this.session = session;
        }

        @Override
        public Optional<WebSession> find(String id) {
            return session.getSessionId().equals(id) ? Optional.of(session) : Optional.empty();
        }
    }
}
