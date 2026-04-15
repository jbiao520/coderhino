package com.coderhino.state;

import com.coderhino.types.Message;
import com.coderhino.types.PermissionMode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class SessionStoreTest {

    @Test
    void loadSessionRestoresCompletedTurnActivityRoundTrip(@TempDir Path tempDir) {
        var store = new SessionStore(new ObjectMapper().registerModule(new JavaTimeModule()), tempDir);
        var runtime = SessionRuntime.create();
        var state = new AppState(
            false,
            "MiniMax-M2.5",
            tempDir.toAbsolutePath().normalize().toString(),
            false,
            true,
            PermissionMode.BYPASS,
            0.0,
            runtime,
            java.util.List.of()
        );

        var userEnvelope = store.recordMessage(state, new Message.UserMessage("Hello"));
        var assistantState = state.withSessionRuntime(state.sessionRuntime().append(userEnvelope));
        var assistantEnvelope = store.recordMessage(assistantState, new Message.AssistantMessage("Done"));
        var persistedState = assistantState.withSessionRuntime(assistantState.sessionRuntime().append(assistantEnvelope));
        var activity = new SessionRuntime.CompletedTurnActivity(
            assistantEnvelope.uuid(),
            java.util.List.of(
                new SessionRuntime.CompletedTurnActivity.ActivityItem("thinking", "Plan carefully", null, null, null, null),
                new SessionRuntime.CompletedTurnActivity.ActivityItem("tool", null, "glob", "tool-1", "{\"pattern\":\"*.java\"}", "src/Main.java")
            ),
            new SessionRuntime.CompletedTurnActivity.FileChangeSummary(1, java.util.List.of(), java.util.List.of("src/Main.java"), java.util.List.of())
        );
        store.appendCompletedTurnActivity(persistedState, activity);

        var loaded = store.loadSession(runtime.sessionId(), state.cwd());

        assertEquals(2, loaded.transcript().size());
        assertEquals(1, loaded.completedTurnActivities().size());
        assertEquals(assistantEnvelope.uuid(), loaded.completedTurnActivities().get(0).assistantMessageId());
        assertEquals(2, loaded.completedTurnActivities().get(0).transcript().size());
        assertEquals("glob", loaded.completedTurnActivities().get(0).transcript().get(1).toolName());
        assertEquals(1, loaded.completedTurnActivities().get(0).fileSummary().totalChanges());
    }

    @Test
    void loadSessionDropsCompletedTurnActivityWithoutMatchingAssistantMessage(@TempDir Path tempDir) {
        var store = new SessionStore(new ObjectMapper().registerModule(new JavaTimeModule()), tempDir);
        var runtime = SessionRuntime.create();
        var state = new AppState(
            false,
            "MiniMax-M2.5",
            tempDir.toAbsolutePath().normalize().toString(),
            false,
            true,
            PermissionMode.BYPASS,
            0.0,
            runtime,
            java.util.List.of()
        );

        store.recordMessage(state, new Message.UserMessage("Hello"));
        store.appendCompletedTurnActivity(
            state,
            new SessionRuntime.CompletedTurnActivity(
                java.util.UUID.randomUUID(),
                java.util.List.of(new SessionRuntime.CompletedTurnActivity.ActivityItem("thinking", "Orphaned", null, null, null, null)),
                null
            )
        );

        var loaded = store.loadSession(runtime.sessionId(), state.cwd());

        assertEquals(1, loaded.transcript().size());
        assertEquals(0, loaded.completedTurnActivities().size());
        assertNull(loaded.customTitle());
    }
}
