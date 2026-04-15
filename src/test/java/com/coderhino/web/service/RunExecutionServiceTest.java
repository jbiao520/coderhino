package com.coderhino.web.service;

import com.coderhino.services.ServiceRegistry;
import com.coderhino.web.credentials.ApiCredentials;
import com.coderhino.web.credentials.CredentialsPersistenceService;
import com.coderhino.web.credentials.ProviderConfigResolver;
import com.coderhino.web.dto.RunDto;
import com.coderhino.web.events.SessionEvent;
import com.coderhino.web.events.SessionEventBus;
import com.coderhino.web.notifications.CompletionNotificationStore;
import com.coderhino.state.SessionStore;
import com.coderhino.web.session.WebSession;
import com.coderhino.web.session.WebSessionRegistry;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RunExecutionServiceTest {

    private WebSessionRegistry createStubRegistry() {
        return new WebSessionRegistry(
            new com.coderhino.web.session.SessionPersistenceService(
                Path.of(System.getProperty("java.io.tmpdir"), "test-sessions-reg-" + System.currentTimeMillis())
            ),
            new SessionStore(
                new ObjectMapper(),
                Path.of(System.getProperty("java.io.tmpdir"), "test-project-reg-" + System.currentTimeMillis())
            ),
            new com.coderhino.web.events.SessionEventBus(new ObjectMapper()),
            new com.coderhino.web.project.ProjectPersistenceService() {
                @Override
                public Optional<com.coderhino.web.project.Project> find(String id) {
                    return Optional.empty();
                }
            }
        );
    }

    @Test
    void executeAsyncSetsTerminalStateAndClearsActiveRun() {
        var eventBus = new CapturingEventBus();
        var registry = createStubRegistry();
        var completionStore = new CompletionNotificationStore();
        var service = new RunExecutionService(eventBus, registry, ServiceRegistry.createDefault(Path.of("").toAbsolutePath().normalize()), completionStore);
        var session = WebSession.create("ses-exec-1");
        session.getActiveRun().set(true);
        session.setActiveRunId("run-exec-1");

        service.executeAsync(session, "run-exec-1", "hello");

        var status = session.getCurrentRunStatus();
        assertTrue(status == RunDto.RunStatus.COMPLETED || status == RunDto.RunStatus.FAILED,
            "Expected COMPLETED or FAILED but was " + status);
        assertFalse(session.getActiveRun().get());
        assertNull(session.getActiveRunId());
        assertNotNull(eventBus.lastEvent);
    }

    @Test
    void executeAsyncSkipsSessionUpdateIfRunIdNoLongerActive() {
        var eventBus = new CapturingEventBus();
        var registry = createStubRegistry();
        var service = new RunExecutionService(eventBus, registry, ServiceRegistry.createDefault(Path.of("").toAbsolutePath().normalize()), new CompletionNotificationStore());
        var session = WebSession.create("ses-exec-2");
        session.getActiveRun().set(true);
        session.setActiveRunId("run-exec-2");

        service.executeAsync(session, "run-different", "hello");

        assertEquals("run-exec-2", session.getActiveRunId());
        assertTrue(session.getActiveRun().get());
    }

    @Test
    void executeAsyncFailsWhenSelectedProviderIsMissing(@TempDir Path tempDir) {
        var eventBus = new CapturingEventBus();
        var registry = createStubRegistry();
        var completionStore = new CompletionNotificationStore();
        var credentialsService = new CredentialsPersistenceService(tempDir.resolve("api-credentials.json"));
        var settingsService = new com.coderhino.web.settings.SettingsPersistenceService(tempDir.resolve("web-settings.json"));
        var credentials = new ApiCredentials();
        credentials.setDefaultProviderId("provider-1");
        credentials.setProviders(java.util.List.of(
            new ApiCredentials.ApiProvider("provider-1", "Anthropic", "secret-1", "https://api.anthropic.com", java.util.List.of("MiniMax-M2.5"))
        ));
        credentialsService.save(credentials);

        var service = new RunExecutionService(eventBus, registry, ServiceRegistry.createDefault(Path.of("").toAbsolutePath().normalize()), completionStore) {
            @Override
            protected ProviderConfigResolver createProviderConfigResolver() {
                return new ProviderConfigResolver(credentialsService, settingsService);
            }
        };
        var session = WebSession.create("ses-exec-3");
        session.setProviderId("provider-missing");
        session.getActiveRun().set(true);
        session.setActiveRunId("run-exec-3");

        service.executeAsync(session, "run-exec-3", "hello");

        assertEquals(RunDto.RunStatus.FAILED, session.getCurrentRunStatus());
        assertFalse(session.getActiveRun().get());
        assertNull(session.getActiveRunId());
        assertNotNull(eventBus.lastEvent);
        assertEquals(SessionEvent.EventType.failed, eventBus.lastEvent.type());
        var payload = (SessionEvent.RunPayload) eventBus.lastEvent.payload();
        assertTrue(payload.error().contains("provider-missing"));
    }

    private static final class CapturingEventBus extends SessionEventBus {
        SessionEvent lastEvent;

        private CapturingEventBus() {
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
