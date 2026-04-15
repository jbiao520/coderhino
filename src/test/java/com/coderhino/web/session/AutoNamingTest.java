package com.coderhino.web.session;

import com.coderhino.web.events.SessionEventBus;
import com.coderhino.web.project.ProjectPersistenceService;
import com.coderhino.types.Message;
import com.coderhino.state.AppState;
import com.coderhino.state.BootstrapState;
import com.coderhino.state.SessionStore;
import com.coderhino.types.PermissionMode;
import com.coderhino.state.SessionRuntime;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class AutoNamingTest {

    private SessionStore createSessionStore(Path root) {
        return new SessionStore(new ObjectMapper().registerModule(new JavaTimeModule()), root);
    }

    private WebSessionRegistry createRegistry(Path tempDir) {
        return new WebSessionRegistry(
            new SessionPersistenceService(tempDir),
            createSessionStore(tempDir.resolve("projects")),
            new SessionEventBus(new ObjectMapper()),
            new ProjectPersistenceService() {
                @Override
                public Optional<com.coderhino.web.project.Project> find(String id) {
                    return Optional.empty();
                }
            }
        );
    }

    private WebSession createSessionWithMessages(String... messages) {
        var initialState = new AppState(
            false, "MiniMax-M2.5", "/tmp", false, true,
            PermissionMode.BYPASS, 0.0, SessionRuntime.create(), List.of()
        );
        var bootstrapState = new BootstrapState(initialState);
        for (String msg : messages) {
            bootstrapState.addMessage(new Message.UserMessage(msg));
        }
        return new WebSession("ses-test", Instant.now(), bootstrapState);
    }

    @Test
    void autoNameFromShortMessage(@TempDir Path tempDir) {
        var registry = createRegistry(tempDir);
        var session = createSessionWithMessages("Hello world");
        registry.autoNameSession(session);
        assertEquals("Hello world", session.getName());
    }

    @Test
    void autoNameTruncatesLongMessage(@TempDir Path tempDir) {
        var registry = createRegistry(tempDir);
        var longMsg = "a".repeat(120);
        var session = createSessionWithMessages(longMsg);
        registry.autoNameSession(session);
        assertEquals(81, session.getName().length());
        assertEquals(80 + "…".length(), session.getName().length());
    }

    @Test
    void autoNameCleansMultiline(@TempDir Path tempDir) {
        var registry = createRegistry(tempDir);
        var session = createSessionWithMessages("line one\nline two\r\nline three");
        registry.autoNameSession(session);
        assertEquals("line one line two line three", session.getName());
    }

    @Test
    void autoNameSkipsAlreadyNamed(@TempDir Path tempDir) {
        var registry = createRegistry(tempDir);
        var session = createSessionWithMessages("Hello world");
        session.setName("Existing Name");
        registry.autoNameSession(session);
        assertEquals("Existing Name", session.getName());
    }

    @Test
    void autoNameSkipsIfNoUserMessages(@TempDir Path tempDir) {
        var registry = createRegistry(tempDir);
        var session = createSessionWithMessages();
        registry.autoNameSession(session);
        assertNull(session.getName());
    }
}
