package com.coderhino.web.service;

import com.coderhino.query.ModelClient;
import com.coderhino.query.ModelResponse;
import com.coderhino.query.ProviderApiType;
import com.coderhino.query.QueryRequest;
import com.coderhino.services.ServiceRegistry;
import com.coderhino.types.Message;
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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RunExecutionServiceTest {

    private static ApiCredentials.ApiProvider.ModelConfig model(String id) {
        return new ApiCredentials.ApiProvider.ModelConfig(id, 128000L);
    }

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
            new ApiCredentials.ApiProvider("provider-1", "Anthropic", "secret-1", "https://api.anthropic.com", java.util.List.of(model("MiniMax-M2.5")))
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

    @Test
    void executeAsyncPersistsVisiblePromptButSendsRawPromptToModel() {
        var eventBus = new CapturingEventBus();
        var registry = createStubRegistry();
        QueryRequest[] captured = new QueryRequest[1];
        var service = new RunExecutionService(eventBus, registry, ServiceRegistry.createDefault(Path.of("").toAbsolutePath().normalize()), new CompletionNotificationStore()) {
            @Override
            protected ProviderConfigResolver createProviderConfigResolver() {
                return new ProviderConfigResolver() {
                    @Override
                    public ResolvedConfig resolve(String providerId, String model) {
                        return new ResolvedConfig("provider-1", "secret", "https://example.test", "MiniMax-M2.5", ProviderApiType.CLAUDE_CODE, 128000L);
                    }
                };
            }

            @Override
            protected ModelClient createModelClient(ProviderConfigResolver.ResolvedConfig config) {
                return (bootstrapState, request) -> {
                    captured[0] = request;
                    return new ModelResponse.AssistantReply("streamed reply");
                };
            }
        };
        var session = WebSession.create("ses-exec-visible");
        session.getActiveRun().set(true);
        session.setActiveRunId("run-visible");

        service.executeAsync(session, "run-visible", "/init project", "inspect repo first: project");

        assertNotNull(captured[0]);
        assertEquals(1, captured[0].messages().stream().filter(Message.UserMessage.class::isInstance).count());
        assertEquals("/init project", captured[0].messages().get(0).content());

        var messages = session.getAppState().messages();
        assertEquals(2, messages.size());
        assertEquals("inspect repo first: project", messages.get(0).content());
        assertEquals("streamed reply", messages.get(1).content());
    }

    @Test
    void cancelRunInterruptsTrackedExecutionThread() throws Exception {
        var eventBus = new CapturingEventBus();
        var registry = createStubRegistry();
        var service = new RunExecutionService(eventBus, registry, ServiceRegistry.createDefault(Path.of("").toAbsolutePath().normalize()), new CompletionNotificationStore());
        var session = WebSession.create("ses-exec-cancel");
        var started = new CountDownLatch(1);
        var interrupted = new CountDownLatch(1);

        var sink = new com.coderhino.web.events.SseQueryEventSink(session.getSessionId(), "run-cancel-thread", eventBus);
        var activeSinksField = RunExecutionService.class.getDeclaredField("activeSinks");
        activeSinksField.setAccessible(true);
        @SuppressWarnings("unchecked")
        var activeSinks = (java.util.Map<String, com.coderhino.web.events.SseQueryEventSink>) activeSinksField.get(service);

        var activeThreadsField = RunExecutionService.class.getDeclaredField("activeThreads");
        activeThreadsField.setAccessible(true);
        @SuppressWarnings("unchecked")
        var activeThreads = (java.util.Map<String, Thread>) activeThreadsField.get(service);

        var worker = new Thread(() -> {
            activeSinks.put("run-cancel-thread", sink);
            activeThreads.put("run-cancel-thread", Thread.currentThread());
            started.countDown();
            try {
                Thread.sleep(TimeUnit.SECONDS.toMillis(30));
            } catch (InterruptedException e) {
                interrupted.countDown();
                Thread.currentThread().interrupt();
            } finally {
                activeSinks.remove("run-cancel-thread", sink);
                activeThreads.remove("run-cancel-thread", Thread.currentThread());
            }
        });
        worker.start();

        assertTrue(started.await(2, TimeUnit.SECONDS));
        assertTrue(service.cancelRun("run-cancel-thread"));
        assertTrue(interrupted.await(2, TimeUnit.SECONDS));
        worker.join(2000);
        assertTrue(sink.isCancelled());
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
