package com.coderhino.web.service;

import com.coderhino.query.ModelClient;
import com.coderhino.query.ModelClientFactory;
import com.coderhino.query.QueryEngine;
import com.coderhino.state.SessionStore;
import com.coderhino.services.ServiceRegistry;
import com.coderhino.services.tasks.TaskOriginContext;
import com.coderhino.services.summary.FileChangeTracker;
import com.coderhino.tools.ToolRegistry;
import com.coderhino.web.credentials.ProviderConfigResolver;
import com.coderhino.web.dto.RunDto;
import com.coderhino.web.events.SessionEventBus;
import com.coderhino.web.events.SseQueryEventSink;
import com.coderhino.web.notifications.CompletionNotificationStore;
import com.coderhino.web.session.WebSession;
import com.coderhino.web.session.WebSessionRegistry;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.util.UUID;

@Service
public class RunExecutionService {

    static final String WEB_RESPONSE_FORMAT_PROMPT = String.join(System.lineSeparator(),
        "Format every web chat response as structured, scannable Markdown.",
        "Use short headings, short paragraphs, and flat bullet lists instead of long wall-of-text sections.",
        "For feature requests or change proposals, use this exact workflow:",
        "- Start with a bold top line in the form **Proposed Change: <title>**.",
        "- Follow with a short high-level overview of purpose and user value.",
        "- Include a heading exactly named ### Brainstorming & Exploration for reasoning and research details.",
        "- Include explicit action-oriented sections for implementation readiness, artifacts, and next action when relevant.",
        "- End with a direct next-step instruction when the response prepares work for implementation.",
        "Never respond with a single unstructured wall of text."
    );

    private final SessionEventBus eventBus;
    private final FileChangeTracker fileChangeTracker;
    private final WebSessionRegistry sessionRegistry;
    private final ServiceRegistry serviceRegistry;
    private final CompletionNotificationStore completionNotificationStore;
    private final SessionStore sessionStore;
    private final java.util.Map<String, SseQueryEventSink> activeSinks = new java.util.concurrent.ConcurrentHashMap<>();
    private final java.util.Map<String, Thread> activeThreads = new java.util.concurrent.ConcurrentHashMap<>();

    public RunExecutionService(SessionEventBus eventBus, WebSessionRegistry sessionRegistry) {
        this(eventBus, sessionRegistry, ServiceRegistry.createDefault(Path.of("").toAbsolutePath().normalize()), new CompletionNotificationStore());
    }

    @Autowired
    public RunExecutionService(SessionEventBus eventBus, WebSessionRegistry sessionRegistry, ServiceRegistry serviceRegistry,
                               CompletionNotificationStore completionNotificationStore) {
        this(eventBus, serviceRegistry.fileChangeTracker(), sessionRegistry, serviceRegistry, completionNotificationStore);
    }

    public RunExecutionService(SessionEventBus eventBus, FileChangeTracker fileChangeTracker, WebSessionRegistry sessionRegistry) {
        this(eventBus, fileChangeTracker, sessionRegistry, ServiceRegistry.createDefault(Path.of("").toAbsolutePath().normalize()), new CompletionNotificationStore());
    }

    public RunExecutionService(SessionEventBus eventBus, FileChangeTracker fileChangeTracker,
                               WebSessionRegistry sessionRegistry, ServiceRegistry serviceRegistry,
                               CompletionNotificationStore completionNotificationStore) {
        this.eventBus = eventBus;
        this.fileChangeTracker = fileChangeTracker;
        this.sessionRegistry = sessionRegistry;
        this.serviceRegistry = serviceRegistry;
        this.completionNotificationStore = completionNotificationStore;
        this.sessionStore = sessionRegistry != null ? sessionRegistry.getSessionStore() : null;
    }

    @Async
    public void executeAsync(WebSession session, String runId, String input) {
        executeAsync(session, runId, input, input);
    }

    @Async
    public void executeAsync(WebSession session, String runId, String input, String visiblePrompt) {
        activeThreads.put(runId, Thread.currentThread());
        var sessionUuid = toUuid(session.getSessionId());
        var projectId = sessionRegistry.getProjectIdForSession(session.getSessionId()).orElse(null);
        var sink = new SseQueryEventSink(session.getSessionId(), runId, eventBus, fileChangeTracker, sessionUuid, session.getBootstrapState(), session, projectId, sessionStore);
        activeSinks.put(runId, sink);
        try {
            var config = createProviderConfigResolver().resolve(session.getProviderId(), session.getAppState().model());
            var modelClient = createModelClient(config);
            var engine = createQueryEngine(config, modelClient);
            com.coderhino.query.QueryResult result;
            try (var ignored = TaskOriginContext.open(projectId, session.getSessionId())) {
                result = engine.execute(session.getBootstrapState(), input, visiblePrompt, sink);
            }
            if (sink.isCancelled()) {
                return;
            }
            if (runId.equals(session.getActiveRunId())) {
                completionNotificationStore.recordAiRunCompletion(runId, session.getSessionId(), projectId, java.time.Instant.now());
                session.setCurrentRunStatus(result.isError() ? RunDto.RunStatus.FAILED : RunDto.RunStatus.COMPLETED);
                session.getActiveRun().set(false);
                session.setActiveRunId(null);
                session.clearActiveRunReplay();
                sessionRegistry.persistSessionState(session);
            }
            if (session.getName() == null) {
                sessionRegistry.autoNameSession(session);
            }
        } catch (Exception e) {
            if (sink.isCancelled() || e instanceof java.util.concurrent.CancellationException) {
                return;
            }
            sink.onError(e.getMessage());
            if (runId.equals(session.getActiveRunId())) {
                session.setCurrentRunStatus(RunDto.RunStatus.FAILED);
                session.getActiveRun().set(false);
                session.setActiveRunId(null);
                session.clearActiveRunReplay();
                sessionRegistry.persistSessionState(session);
            }
        } finally {
            activeSinks.remove(runId, sink);
            activeThreads.remove(runId, Thread.currentThread());
        }
    }

    public boolean cancelRun(String runId) {
        var sink = activeSinks.get(runId);
        var thread = activeThreads.get(runId);
        var cancelled = false;
        if (sink != null) {
            cancelled = sink.cancel();
        }
        if (thread != null) {
            thread.interrupt();
            cancelled = true;
        }
        return cancelled;
    }

    public boolean answerPendingQuestion(String runId, String toolUseId, String answer) {
        var sink = activeSinks.get(runId);
        return sink != null && sink.answerPendingQuestion(toolUseId, answer);
    }

    public boolean hasPendingQuestion(String runId, String toolUseId) {
        var sink = activeSinks.get(runId);
        return sink != null && sink.hasPendingQuestion(toolUseId);
    }

    private static UUID toUuid(String sessionId) {
        try {
            return UUID.fromString(sessionId);
        } catch (Exception e) {
            return UUID.nameUUIDFromBytes(sessionId.getBytes());
        }
    }

    protected ProviderConfigResolver createProviderConfigResolver() {
        return new ProviderConfigResolver();
    }

    protected ModelClient createModelClient(ProviderConfigResolver.ResolvedConfig config) {
        return ModelClientFactory.create(
            config.getModel(),
            config.getApiKey(),
            config.getBaseUrl(),
            config.getApiType(),
            config.getContextWindow()
        );
    }

    protected QueryEngine createQueryEngine(ProviderConfigResolver.ResolvedConfig config, ModelClient modelClient) {
        return new QueryEngine(
            ToolRegistry.createDefault(),
            modelClient,
            new com.coderhino.permissions.PermissionChecker(),
            new com.coderhino.context.ContextCollector(),
            serviceRegistry,
            null,
            WEB_RESPONSE_FORMAT_PROMPT
        );
    }
}
