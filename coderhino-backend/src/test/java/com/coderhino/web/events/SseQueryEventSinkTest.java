package com.coderhino.web.events;

import com.coderhino.state.SessionStore;
import com.coderhino.types.Message;
import com.coderhino.web.controller.SessionController;
import com.coderhino.web.dto.SessionDto;
import com.coderhino.web.session.SessionPersistenceService;
import com.coderhino.web.session.WebSession;
import com.coderhino.web.session.WebSessionRegistry;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SseQueryEventSinkTest {

    @Test
    void completedEventIsPublishedAfterPersistedActivityIsQueryable(@TempDir Path tempDir) {
        var metadataDir = tempDir.resolve("metadata");
        var transcriptDir = tempDir.resolve("transcripts");
        var eventBus = new AssertingEventBus();
        var sessionStore = new SessionStore(new ObjectMapper().registerModule(new JavaTimeModule()), transcriptDir);
        var registry = new WebSessionRegistry(
            new SessionPersistenceService(metadataDir),
            sessionStore,
            eventBus,
            new com.coderhino.web.project.ProjectPersistenceService() {
                @Override
                public Optional<com.coderhino.web.project.Project> find(String id) {
                    return Optional.of(new com.coderhino.web.project.Project(
                        id,
                        "Project",
                        transcriptDir.toString(),
                        java.time.Instant.now(),
                        java.time.Instant.now(),
                        true,
                        java.util.List.of(com.coderhino.web.project.Worktree.defaultForProject(transcriptDir.toString()))
                    ));
                }
            }
        );

        var session = registry.createSessionForProject("project-1").orElseThrow();
        session.getBootstrapState().addMessage(new Message.UserMessage("Inspect run"));
        session.getBootstrapState().addMessage(new Message.AssistantMessage("Done"));
        session.startActiveRunReplay("run-1");
        session.recordReplayThinkingDelta("run-1", "Plan carefully");
        session.recordReplayToolCall("run-1", "glob", "tool-1", "{\"pattern\":\"*.java\"}");
        session.recordReplayToolResult("run-1", "glob", "tool-1", "src/Main.java");
        eventBus.controller = new SessionController(registry);
        eventBus.expectedSessionId = session.getSessionId();
        eventBus.expectActivityTimeline = true;

        var sink = new SseQueryEventSink(
            session.getSessionId(),
            "run-1",
            eventBus,
            null,
            session.getAppState().sessionRuntime().sessionId(),
            session.getBootstrapState(),
            session,
            "project-1",
            sessionStore
        );

        sink.onCompleted("Done");

        assertNotNull(eventBus.lastEvent);
        assertEquals(SessionEvent.EventType.completed, eventBus.lastEvent.type());
        assertEquals(1, session.getAppState().sessionRuntime().completedTurnActivities().size());
    }

    @Test
    void completedEventWithoutReviewableActivityDoesNotCreateSyntheticTimeline(@TempDir Path tempDir) {
        var metadataDir = tempDir.resolve("metadata");
        var transcriptDir = tempDir.resolve("transcripts");
        var eventBus = new AssertingEventBus();
        var sessionStore = new SessionStore(new ObjectMapper().registerModule(new JavaTimeModule()), transcriptDir);
        var registry = new WebSessionRegistry(
            new SessionPersistenceService(metadataDir),
            sessionStore,
            eventBus,
            new com.coderhino.web.project.ProjectPersistenceService() {
                @Override
                public Optional<com.coderhino.web.project.Project> find(String id) {
                    return Optional.of(new com.coderhino.web.project.Project(
                        id,
                        "Project",
                        transcriptDir.toString(),
                        java.time.Instant.now(),
                        java.time.Instant.now(),
                        true,
                        java.util.List.of(com.coderhino.web.project.Worktree.defaultForProject(transcriptDir.toString()))
                    ));
                }
            }
        );

        var session = registry.createSessionForProject("project-1").orElseThrow();
        session.getBootstrapState().addMessage(new Message.UserMessage("Inspect run"));
        session.getBootstrapState().addMessage(new Message.AssistantMessage("Done"));
        session.startActiveRunReplay("run-2");
        eventBus.controller = new SessionController(registry);
        eventBus.expectedSessionId = session.getSessionId();
        eventBus.expectActivityTimeline = false;

        var sink = new SseQueryEventSink(
            session.getSessionId(),
            "run-2",
            eventBus,
            null,
            session.getAppState().sessionRuntime().sessionId(),
            session.getBootstrapState(),
            session,
            "project-1",
            sessionStore
        );

        sink.onCompleted("Done");

        assertNotNull(eventBus.lastEvent);
        assertEquals(SessionEvent.EventType.completed, eventBus.lastEvent.type());
        assertEquals(0, session.getAppState().sessionRuntime().completedTurnActivities().size());
    }

    @Test
    void duplicateCompletedTurnPersistenceIsIgnoredAfterCompletion(@TempDir Path tempDir) {
        var metadataDir = tempDir.resolve("metadata");
        var transcriptDir = tempDir.resolve("transcripts");
        var eventBus = new AssertingEventBus();
        var sessionStore = new SessionStore(new ObjectMapper().registerModule(new JavaTimeModule()), transcriptDir);
        var registry = new WebSessionRegistry(
            new SessionPersistenceService(metadataDir),
            sessionStore,
            eventBus,
            new com.coderhino.web.project.ProjectPersistenceService() {
                @Override
                public Optional<com.coderhino.web.project.Project> find(String id) {
                    return Optional.of(new com.coderhino.web.project.Project(
                        id,
                        "Project",
                        transcriptDir.toString(),
                        java.time.Instant.now(),
                        java.time.Instant.now(),
                        true,
                        java.util.List.of(com.coderhino.web.project.Worktree.defaultForProject(transcriptDir.toString()))
                    ));
                }
            }
        );

        var session = registry.createSessionForProject("project-1").orElseThrow();
        session.getBootstrapState().addMessage(new Message.UserMessage("Inspect run"));
        session.getBootstrapState().addMessage(new Message.AssistantMessage("Done"));
        session.startActiveRunReplay("run-3");
        session.recordReplayThinkingDelta("run-3", "Plan carefully");
        session.recordReplayToolCall("run-3", "glob", "tool-1", "{\"pattern\":\"*.java\"}");
        session.recordReplayToolResult("run-3", "glob", "tool-1", "src/Main.java");

        var sink = new SseQueryEventSink(
            session.getSessionId(),
            "run-3",
            eventBus,
            null,
            session.getAppState().sessionRuntime().sessionId(),
            session.getBootstrapState(),
            session,
            "project-1",
            sessionStore
        );

        sink.onCompleted("Done");
        var persistedActivity = session.getAppState().sessionRuntime().completedTurnActivities().get(0);
        sessionStore.replaceTranscript(
            session.getAppState(),
            session.getAppState().sessionRuntime().transcript()
        );
        sessionStore.appendCompletedTurnActivity(session.getAppState(), persistedActivity);

        var reloaded = sessionStore.loadSession(
            session.getAppState().sessionRuntime().sessionId(),
            session.getAppState().cwd()
        );

        assertEquals(1, reloaded.completedTurnActivities().size());
        assertEquals(1, session.getAppState().sessionRuntime().completedTurnActivities().size());
    }

    @Test
    void cancelStopsPublishingFurtherEvents() {
        var eventBus = new AssertingEventBus();
        var session = WebSession.create("ses-cancel-events");
        session.startActiveRunReplay("run-cancel-events");
        var sink = new SseQueryEventSink(session.getSessionId(), "run-cancel-events", eventBus, null, null, session.getBootstrapState(), session, null, null);

        assertTrue(sink.cancel());

        sink.onTextChunk("late text");
        sink.onStatus("Retrying LLM request: attempt 2");
        sink.onToolCall("glob", "tool-1", "{}");
        sink.onToolResult("glob", "tool-1", "result");
        sink.onCompleted("late completion");
        sink.onError("late error");

        assertNull(eventBus.lastEvent);
        assertTrue(sink.isCancelled());
    }

    @Test
    void cancelReleasesPendingQuestionWithoutProducingAnswer() throws Exception {
        var eventBus = new AssertingEventBus();
        var session = WebSession.create("ses-cancel-question");
        session.startActiveRunReplay("run-cancel-question");
        var sink = new SseQueryEventSink(session.getSessionId(), "run-cancel-question", eventBus, null, null, session.getBootstrapState(), session, null, null);
        var started = new CountDownLatch(1);
        var finished = new CountDownLatch(1);
        var answerRef = new java.util.concurrent.atomic.AtomicReference<String>();

        var worker = new Thread(() -> {
            started.countDown();
            answerRef.set(sink.onAskUserQuestion("tool-q", "Which file?", java.util.List.of("A", "B")));
            finished.countDown();
        });
        worker.start();

        assertTrue(started.await(2, TimeUnit.SECONDS));
        assertTrue(sink.cancel());
        assertTrue(finished.await(2, TimeUnit.SECONDS));
        worker.join(2000);

        assertNull(answerRef.get());
        assertNull(session.getPendingQuestion());
        assertFalse(sink.answerPendingQuestion("tool-q", "A"));
    }

    private static final class AssertingEventBus extends SessionEventBus {
        private SessionEvent lastEvent;
        private SessionController controller;
        private String expectedSessionId;
        private boolean expectActivityTimeline;

        private AssertingEventBus() {
            super(new ObjectMapper());
        }

        @Override
        public void publish(String sessionId, SessionEvent event, String runId, Long sequence) {
            this.lastEvent = event;
            if (event.type() != SessionEvent.EventType.completed || controller == null || expectedSessionId == null) {
                return;
            }
            var response = controller.getSession(expectedSessionId);
            var body = response.getBody();
            assertNotNull(body);
            assertEquals(2, body.getMessages().size());
            SessionDto.MessageDto assistant = body.getMessages().get(1);
            if (!expectActivityTimeline) {
                assertNull(assistant.getActivityTimeline());
                return;
            }
            assertNotNull(assistant.getActivityTimeline());
            assertEquals(2, assistant.getActivityTimeline().size());
            assertEquals("thinking", assistant.getActivityTimeline().get(0).getKind());
            assertEquals("glob", assistant.getActivityTimeline().get(1).getToolName());
        }
    }
}
