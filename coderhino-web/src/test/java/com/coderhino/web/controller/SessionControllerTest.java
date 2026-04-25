package com.coderhino.web.controller;

import com.coderhino.web.dto.SessionDto;
import com.coderhino.web.dto.SessionContextDto;
import com.coderhino.web.dto.SessionGitDiffDto;
import com.coderhino.web.dto.SessionGitStatusDto;
import com.coderhino.web.dto.ErrorResponse;
import com.coderhino.state.SessionStore;
import com.coderhino.config.credentials.ApiCredentials;
import com.coderhino.config.credentials.CredentialsPersistenceService;
import com.coderhino.web.git.SessionGitStatusException;
import com.coderhino.web.git.SessionGitStatusService;
import com.coderhino.config.settings.SettingsPersistenceService;
import com.coderhino.web.session.WebSession;
import com.coderhino.web.session.WebSessionRegistry;
import com.coderhino.web.events.SessionEventBus;
import com.coderhino.types.Message;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class SessionControllerTest {

    private static ApiCredentials.ApiProvider.ModelConfig model(String id) {
        return new ApiCredentials.ApiProvider.ModelConfig(id, 128000L);
    }

    private static SessionStore createSessionStore(Path root) {
        return new SessionStore(new ObjectMapper().registerModule(new JavaTimeModule()), root);
    }

    private WebSessionRegistry createRegistry() {
        return new WebSessionRegistry(
            new com.coderhino.web.session.SessionPersistenceService(
                java.nio.file.Path.of(System.getProperty("java.io.tmpdir"), "test-sessions-" + System.currentTimeMillis())
            ),
            createSessionStore(java.nio.file.Path.of(System.getProperty("java.io.tmpdir"), "test-project-sessions-" + System.currentTimeMillis())),
            new SessionEventBus(new ObjectMapper()),
            new com.coderhino.web.project.ProjectPersistenceService() {
                @Override
                public Optional<com.coderhino.web.project.Project> find(String id) {
                    return Optional.empty();
                }
            }
        );
    }

    private WebSessionRegistry createRegistry(Path metadataDir, Path transcriptDir,
                                              CredentialsPersistenceService credentialsService,
                                              SettingsPersistenceService settingsService) {
        return new WebSessionRegistry(
            new com.coderhino.web.session.SessionPersistenceService(metadataDir),
            createSessionStore(transcriptDir),
            new SessionEventBus(new ObjectMapper()),
            new com.coderhino.web.project.ProjectPersistenceService() {
                @Override
                public Optional<com.coderhino.web.project.Project> find(String id) {
                    var managedPath = transcriptDir.resolveSibling("managed-worktree").toString();
                    return Optional.of(new com.coderhino.web.project.Project(
                        id,
                        "Project",
                        transcriptDir.toString(),
                        java.time.Instant.now(),
                        java.time.Instant.now(),
                        true,
                        java.util.List.of(
                            com.coderhino.web.project.Worktree.defaultForProject(transcriptDir.toString()),
                            new com.coderhino.web.project.Worktree("managed-1", "feature-a", managedPath, false, true, java.time.Instant.now())
                        )
                    ));
                }
            },
            credentialsService,
            settingsService
        );
    }

    private WebSessionRegistry createRegistry(Path metadataDir, Path transcriptDir) {
        return new WebSessionRegistry(
            new com.coderhino.web.session.SessionPersistenceService(metadataDir),
            createSessionStore(transcriptDir),
            new SessionEventBus(new ObjectMapper()),
            new com.coderhino.web.project.ProjectPersistenceService() {
                @Override
                public Optional<com.coderhino.web.project.Project> find(String id) {
                    var managedPath = transcriptDir.resolveSibling("managed-worktree").toString();
                    return Optional.of(new com.coderhino.web.project.Project(
                        id,
                        "Project",
                        transcriptDir.toString(),
                        java.time.Instant.now(),
                        java.time.Instant.now(),
                        true,
                        java.util.List.of(
                            com.coderhino.web.project.Worktree.defaultForProject(transcriptDir.toString()),
                            new com.coderhino.web.project.Worktree("managed-1", "feature-a", managedPath, false, true, java.time.Instant.now())
                        )
                    ));
                }
            }
        );
    }

    @Test
    void patchSessionReturns200WithUpdatedName() {
        var registry = createRegistry();
        var session = WebSession.create("ses-1");
        try {
            var field = registry.getClass().getDeclaredField("sessions");
            field.setAccessible(true);
            @SuppressWarnings("unchecked")
            var map = (java.util.concurrent.ConcurrentHashMap<String, WebSession>) field.get(registry);
            map.put("ses-1", session);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        var controller = new SessionController(registry);
        ResponseEntity<SessionDto> response = controller.patchSession("ses-1", Map.of("name", "My Session"));

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("My Session", response.getBody().getName());
    }

    @Test
    void patchSessionReturns404ForUnknownSession() {
        var registry = createRegistry();
        var controller = new SessionController(registry);

        ResponseEntity<SessionDto> response = controller.patchSession("nonexistent", Map.of("name", "Test"));

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertNull(response.getBody());
    }

    @Test
    void patchSessionReturns400ForEmptyBody() {
        var registry = createRegistry();
        var controller = new SessionController(registry);

        ResponseEntity<SessionDto> response = controller.patchSession("ses-1", null);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    }

    @Test
    void patchSessionReturns400ForNullName() {
        var registry = createRegistry();
        var controller = new SessionController(registry);

        ResponseEntity<SessionDto> response = controller.patchSession("ses-1", Map.of());

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    }

    @Test
    void getSessionReturnsPersistedChatHistoryAfterReload(@TempDir Path tempDir) {
        Path metadataDir = tempDir.resolve("metadata");
        Path transcriptDir = tempDir.resolve("transcripts");
        var registry = createRegistry(metadataDir, transcriptDir);
        var created = registry.createSessionForProject("project-1").orElseThrow();
        created.getBootstrapState().addMessage(new Message.UserMessage("Hello"));
        created.getBootstrapState().addMessage(new Message.AssistantMessage("Hi there"));

        var reloadedRegistry = createRegistry(metadataDir, transcriptDir);
        reloadedRegistry.reloadPersistedSessions();
        var controller = new SessionController(reloadedRegistry);

        ResponseEntity<SessionDto> response = controller.getSession(created.getSessionId());

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(2, response.getBody().getMessages().size());
        assertEquals("Hello", response.getBody().getMessages().get(0).getContent());
        assertEquals("Hi there", response.getBody().getMessages().get(1).getContent());
        assertNotNull(response.getBody().getMessages().get(0).getTimestamp());
        assertEquals(Integer.valueOf(0), response.getBody().getMessages().get(0).getRollbackIndex());
        assertNotNull(response.getBody().getMessages().get(1).getTimestamp());
        assertNull(response.getBody().getMessages().get(1).getRollbackIndex());
    }

    @Test
    void getSessionIncludesActiveRunReplayState(@TempDir Path tempDir) {
        Path metadataDir = tempDir.resolve("metadata");
        Path transcriptDir = tempDir.resolve("transcripts");
        var registry = createRegistry(metadataDir, transcriptDir);
        var session = registry.createSessionForProject("project-1").orElseThrow();
        session.getActiveRun().set(true);
        session.setActiveRunId("run-replay");
        session.setCurrentRunStatus(com.coderhino.web.dto.RunDto.RunStatus.RUNNING);
        session.startActiveRunReplay("run-replay");
        session.recordReplayTextChunk("run-replay", "Thinking");
        session.recordReplayToolCall("run-replay", "glob", "tool-1", "{\"pattern\":\"*.ts\"}");
        session.recordReplayToolResult("run-replay", "glob", "tool-1", "src/index.ts");
        session.recordReplayUsage("run-replay", 10, 5, 0, 0, 1, 200);
        var controller = new SessionController(registry);

        ResponseEntity<SessionDto> response = controller.getSession(session.getSessionId());

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertNotNull(response.getBody().getActiveRunState());
        assertEquals("run-replay", response.getBody().getActiveRunState().getRunId());
        assertEquals(Long.valueOf(4), response.getBody().getActiveRunState().getLastSequence());
        assertEquals(2, response.getBody().getActiveRunState().getTranscript().size());
        assertEquals("assistant", response.getBody().getActiveRunState().getTranscript().get(0).getKind());
        assertEquals("Thinking", response.getBody().getActiveRunState().getTranscript().get(0).getContent());
        assertEquals("tool", response.getBody().getActiveRunState().getTranscript().get(1).getKind());
        assertEquals("tool-1", response.getBody().getActiveRunState().getTranscript().get(1).getToolUseId());
        assertEquals("src/index.ts", response.getBody().getActiveRunState().getTranscript().get(1).getOutput());
        assertNotNull(response.getBody().getActiveRunState().getUsage());
        assertEquals(10L, response.getBody().getActiveRunState().getUsage().getInputTokens());
    }

    @Test
    void getSessionIncludesRicherReplayTranscriptItems(@TempDir Path tempDir) {
        Path metadataDir = tempDir.resolve("metadata");
        Path transcriptDir = tempDir.resolve("transcripts");
        var registry = createRegistry(metadataDir, transcriptDir);
        var session = registry.createSessionForProject("project-1").orElseThrow();
        session.getActiveRun().set(true);
        session.setActiveRunId("run-rich");
        session.setCurrentRunStatus(com.coderhino.web.dto.RunDto.RunStatus.RUNNING);
        session.startActiveRunReplay("run-rich");
        session.recordReplayThinkingDelta("run-rich", "Plan A");
        session.recordReplayToolInputDelta("run-rich", "glob", "tool-2", "{\"pattern\":");
        session.recordReplayToolInputDelta("run-rich", "glob", "tool-2", "\"*.java\"}");
        var controller = new SessionController(registry);

        ResponseEntity<SessionDto> response = controller.getSession(session.getSessionId());

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertNotNull(response.getBody().getActiveRunState());
        assertEquals(Long.valueOf(3), response.getBody().getActiveRunState().getLastSequence());
        assertEquals(2, response.getBody().getActiveRunState().getTranscript().size());
        assertEquals("thinking", response.getBody().getActiveRunState().getTranscript().get(0).getKind());
        assertEquals("Plan A", response.getBody().getActiveRunState().getTranscript().get(0).getContent());
        assertEquals("tool-input", response.getBody().getActiveRunState().getTranscript().get(1).getKind());
        assertEquals("glob", response.getBody().getActiveRunState().getTranscript().get(1).getToolName());
        assertEquals("tool-2", response.getBody().getActiveRunState().getTranscript().get(1).getToolUseId());
        assertEquals("{\"pattern\":\"*.java\"}", response.getBody().getActiveRunState().getTranscript().get(1).getArgumentsJson());
    }

    @Test
    void getSessionIncludesRetryStatusReplayTranscriptItems(@TempDir Path tempDir) {
        Path metadataDir = tempDir.resolve("metadata");
        Path transcriptDir = tempDir.resolve("transcripts");
        var registry = createRegistry(metadataDir, transcriptDir);
        var session = registry.createSessionForProject("project-1").orElseThrow();
        session.getActiveRun().set(true);
        session.setActiveRunId("run-retry");
        session.setCurrentRunStatus(com.coderhino.web.dto.RunDto.RunStatus.RUNNING);
        session.startActiveRunReplay("run-retry");
        session.recordReplayStatus("run-retry", "Retrying LLM request: attempt 2 of 5 after service overloaded");
        session.recordReplayThinkingDelta("run-retry", "Plan B");
        var controller = new SessionController(registry);

        ResponseEntity<SessionDto> response = controller.getSession(session.getSessionId());

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertNotNull(response.getBody().getActiveRunState());
        assertEquals(Long.valueOf(2), response.getBody().getActiveRunState().getLastSequence());
        assertEquals(2, response.getBody().getActiveRunState().getTranscript().size());
        assertEquals("status", response.getBody().getActiveRunState().getTranscript().get(0).getKind());
        assertEquals("Retrying LLM request: attempt 2 of 5 after service overloaded", response.getBody().getActiveRunState().getTranscript().get(0).getContent());
        assertEquals("thinking", response.getBody().getActiveRunState().getTranscript().get(1).getKind());
    }

    @Test
    void getSessionIncludesPendingQuestionState(@TempDir Path tempDir) {
        Path metadataDir = tempDir.resolve("metadata");
        Path transcriptDir = tempDir.resolve("transcripts");
        var registry = createRegistry(metadataDir, transcriptDir);
        var session = registry.createSessionForProject("project-1").orElseThrow();
        session.getActiveRun().set(true);
        session.setActiveRunId("run-question");
        session.setCurrentRunStatus(com.coderhino.web.dto.RunDto.RunStatus.WAITING_FOR_USER);
        session.startActiveRunReplay("run-question");
        session.recordReplayPendingQuestion("run-question", "tool-question-1", "Choose one", java.util.List.of("A", "B"));
        var controller = new SessionController(registry);

        ResponseEntity<SessionDto> response = controller.getSession(session.getSessionId());

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertNotNull(response.getBody().getActiveRunState());
        assertNotNull(response.getBody().getActiveRunState().getPendingQuestion());
        assertEquals("tool-question-1", response.getBody().getActiveRunState().getPendingQuestion().getToolUseId());
        assertEquals("Choose one", response.getBody().getActiveRunState().getPendingQuestion().getQuestion());
        assertEquals(java.util.List.of("A", "B"), response.getBody().getActiveRunState().getPendingQuestion().getChoices());
    }

    @Test
    void getSessionIncludesPersistedCompletedTurnActivity(@TempDir Path tempDir) {
        Path metadataDir = tempDir.resolve("metadata");
        Path transcriptDir = tempDir.resolve("transcripts");
        var registry = createRegistry(metadataDir, transcriptDir);
        var session = registry.createSessionForProject("project-1").orElseThrow();
        session.getBootstrapState().addMessage(new Message.UserMessage("Check persisted activity"));
        session.getBootstrapState().addMessage(new Message.AssistantMessage("Done"));
        var assistantEnvelope = session.getAppState().sessionRuntime().transcript().get(1);
        var activity = new com.coderhino.state.SessionRuntime.CompletedTurnActivity(
            assistantEnvelope.uuid(),
            java.util.List.of(
                new com.coderhino.state.SessionRuntime.CompletedTurnActivity.ActivityItem("thinking", "Plan carefully", null, null, null, null),
                new com.coderhino.state.SessionRuntime.CompletedTurnActivity.ActivityItem("tool", null, "glob", "tool-1", "{\"pattern\":\"*.java\"}", "src/Main.java")
            ),
            new com.coderhino.state.SessionRuntime.CompletedTurnActivity.FileChangeSummary(1, java.util.List.of(), java.util.List.of("src/Main.java"), java.util.List.of())
        );
        registry.getSessionStore().appendCompletedTurnActivity(session.getAppState(), activity);
        session.getBootstrapState().update(state -> state.withSessionRuntime(state.sessionRuntime().appendCompletedTurnActivity(activity)));
        var controller = new SessionController(registry);

        ResponseEntity<SessionDto> response = controller.getSession(session.getSessionId());

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(2, response.getBody().getMessages().size());
        var assistantMessage = response.getBody().getMessages().get(1);
        assertNotNull(assistantMessage.getActivityTimeline());
        assertEquals(2, assistantMessage.getActivityTimeline().size());
        assertEquals("thinking", assistantMessage.getActivityTimeline().get(0).getKind());
        assertEquals("glob", assistantMessage.getActivityTimeline().get(1).getToolName());
        assertNotNull(assistantMessage.getFileSummary());
        assertEquals(1, assistantMessage.getFileSummary().getTotalChanges());
    }

    @Test
    void getSessionRestoresPersistedCompletedTurnActivityAfterReload(@TempDir Path tempDir) {
        Path metadataDir = tempDir.resolve("metadata");
        Path transcriptDir = tempDir.resolve("transcripts");
        var registry = createRegistry(metadataDir, transcriptDir);
        var session = registry.createSessionForProject("project-1").orElseThrow();
        session.getBootstrapState().addMessage(new Message.UserMessage("Check persisted activity"));
        session.getBootstrapState().addMessage(new Message.AssistantMessage("Done"));
        var assistantEnvelope = session.getAppState().sessionRuntime().transcript().get(1);
        var activity = new com.coderhino.state.SessionRuntime.CompletedTurnActivity(
            assistantEnvelope.uuid(),
            java.util.List.of(
                new com.coderhino.state.SessionRuntime.CompletedTurnActivity.ActivityItem("thinking", "Plan carefully", null, null, null, null),
                new com.coderhino.state.SessionRuntime.CompletedTurnActivity.ActivityItem("tool", null, "glob", "tool-1", "{\"pattern\":\"*.java\"}", "src/Main.java")
            ),
            new com.coderhino.state.SessionRuntime.CompletedTurnActivity.FileChangeSummary(1, java.util.List.of(), java.util.List.of("src/Main.java"), java.util.List.of())
        );
        registry.getSessionStore().appendCompletedTurnActivity(session.getAppState(), activity);
        session.getBootstrapState().update(state -> state.withSessionRuntime(state.sessionRuntime().appendCompletedTurnActivity(activity)));

        var reloadedRegistry = createRegistry(metadataDir, transcriptDir);
        reloadedRegistry.reloadPersistedSessions();
        var controller = new SessionController(reloadedRegistry);

        ResponseEntity<SessionDto> response = controller.getSession(session.getSessionId());

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(2, response.getBody().getMessages().size());
        var assistantMessage = response.getBody().getMessages().get(1);
        assertNotNull(assistantMessage.getActivityTimeline());
        assertEquals(2, assistantMessage.getActivityTimeline().size());
        assertEquals("thinking", assistantMessage.getActivityTimeline().get(0).getKind());
        assertEquals("glob", assistantMessage.getActivityTimeline().get(1).getToolName());
        assertNotNull(assistantMessage.getFileSummary());
        assertEquals(1, assistantMessage.getFileSummary().getTotalChanges());
        assertNull(response.getBody().getActiveRunState());
    }

    @Test
    void deleteSessionRemovesPersistedTranscript(@TempDir Path tempDir) {
        Path metadataDir = tempDir.resolve("metadata");
        Path transcriptDir = tempDir.resolve("transcripts");
        var registry = createRegistry(metadataDir, transcriptDir);
        var session = registry.createSessionForProject("project-1").orElseThrow();
        session.getBootstrapState().addMessage(new Message.UserMessage("Delete me"));
        var controller = new SessionController(registry);
        var transcriptPath = createSessionStore(transcriptDir)
            .transcriptPath(session.getAppState().cwd(), UUID.fromString(session.getSessionId()));

        assertTrue(java.nio.file.Files.exists(transcriptPath));

        ResponseEntity<Void> response = controller.deleteSession(session.getSessionId());

        assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode());
        assertTrue(registry.find(session.getSessionId()).isEmpty());
        assertFalse(java.nio.file.Files.exists(transcriptPath));
    }

    @Test
    void reloadLegacyProjectScopedSessionMapsToDefaultWorktree(@TempDir Path tempDir) throws Exception {
        Path metadataDir = tempDir.resolve("metadata");
        Path transcriptDir = tempDir.resolve("transcripts");
        java.nio.file.Files.createDirectories(metadataDir);
        java.nio.file.Files.createDirectories(transcriptDir);

        var metadata = new com.coderhino.web.session.SessionMetadata(
            "00000000-0000-0000-0000-000000000123",
            java.time.Instant.parse("2026-04-11T00:00:00Z"),
            "MiniMax-M2.5",
            "BYPASS",
            transcriptDir.toString(),
            "project-1",
            "Legacy Session",
            "main"
        );
        var objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        objectMapper.writerWithDefaultPrettyPrinter().writeValue(metadataDir.resolve(metadata.getSessionId() + ".json").toFile(), metadata);

        var registry = createRegistry(metadataDir, transcriptDir);
        registry.reloadPersistedSessions();
        var controller = new SessionController(registry);

        ResponseEntity<SessionDto> response = controller.getSession(metadata.getSessionId());

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("project-1", response.getBody().getProjectId());
        assertEquals("default", response.getBody().getWorktreeId());
        assertNotNull(response.getBody().getWorktree());
        assertEquals(transcriptDir.toString(), response.getBody().getWorktree().getPath());
    }

    @Test
    void createManagedWorktreeSessionIncludesManagedWorktreeMetadata(@TempDir Path tempDir) throws Exception {
        Path metadataDir = tempDir.resolve("metadata");
        Path transcriptDir = tempDir.resolve("default-worktree");
        java.nio.file.Files.createDirectories(transcriptDir);
        java.nio.file.Files.createDirectories(tempDir.resolve("managed-worktree"));
        var registry = createRegistry(metadataDir, transcriptDir);
        var session = registry.createSessionForProject("project-1", "managed-1").orElseThrow();
        var controller = new SessionController(registry);

        ResponseEntity<SessionDto> response = controller.getSession(session.getSessionId());

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("managed-1", response.getBody().getWorktreeId());
        assertNotNull(response.getBody().getWorktree());
        assertEquals("feature-a", response.getBody().getWorktree().getName());
        assertEquals(tempDir.resolve("managed-worktree").toString(), response.getBody().getWorktree().getPath());
    }

    @Test
    void getSessionIncludesComposerToolbarState(@TempDir Path tempDir) {
        Path metadataDir = tempDir.resolve("metadata");
        Path transcriptDir = tempDir.resolve("transcripts");
        var registry = createRegistry(metadataDir, transcriptDir);
        var session = registry.createSessionForProject("project-1").orElseThrow();
        session.getBootstrapState().update(state -> state.withModel("GPT-5.4").withPermissionMode(com.coderhino.types.PermissionMode.PLAN));
        session.setModelMode("think");
        registry.persistSessionState(session);
        var controller = new SessionController(registry);

        ResponseEntity<SessionDto> response = controller.getSession(session.getSessionId());

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("GPT-5.4", response.getBody().getModel());
        assertTrue(response.getBody().isPlanMode());
        assertFalse(response.getBody().isBuildMode());
        assertTrue(response.getBody().isModelModeSupported());
        assertEquals("think", response.getBody().getModelMode());
        assertEquals(java.util.List.of("default", "low", "high"), response.getBody().getAvailableModelModes());
    }

    @Test
    void getSessionUsesSelectedProviderModels(@TempDir Path tempDir) {
        Path metadataDir = tempDir.resolve("metadata");
        Path transcriptDir = tempDir.resolve("transcripts");
        Path credentialsFile = tempDir.resolve("api-credentials.json");
        Path settingsFile = tempDir.resolve("web-settings.json");
        var credentialsService = new CredentialsPersistenceService(credentialsFile);
        var settingsService = new SettingsPersistenceService(settingsFile);
        var credentials = new ApiCredentials();
        credentials.setDefaultProviderId("provider-2");
        credentials.setProviders(java.util.List.of(
            new ApiCredentials.ApiProvider("provider-1", "Anthropic", "secret-1", "https://api.anthropic.com", java.util.List.of(model("MiniMax-M2.5"))),
            new ApiCredentials.ApiProvider("provider-2", "OpenAI", "secret-2", "https://api.openai.com/v1", java.util.List.of(model("gpt-4o"), model("gpt-4.1")))
        ));
        credentialsService.save(credentials);

        var registry = createRegistry(metadataDir, transcriptDir, credentialsService, settingsService);
        var session = registry.createSessionForProject("project-1").orElseThrow();
        var controller = new SessionController(registry, credentialsService);

        ResponseEntity<SessionDto> response = controller.getSession(session.getSessionId());

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("provider-2", response.getBody().getProviderId());
        assertEquals("gpt-4o", response.getBody().getModel());
        assertEquals(java.util.List.of("gpt-4o", "gpt-4.1"), response.getBody().getAvailableModels());
        assertEquals(2, response.getBody().getAvailableProviders().size());
        assertEquals(java.util.List.of("gpt-4o", "gpt-4.1"), response.getBody().getAvailableProviders().get(1).getModels());
        assertEquals("gpt-4o", response.getBody().getAvailableProviders().get(1).getModelOptions().get(0).getId());
        assertEquals(java.util.List.of(), response.getBody().getAvailableProviders().get(1).getModelOptions().get(0).getAvailableModelModes());
    }

    @Test
    void createSessionUsesProviderModelBeforeSettingsDefault(@TempDir Path tempDir) {
        Path metadataDir = tempDir.resolve("metadata");
        Path transcriptDir = tempDir.resolve("transcripts");
        Path credentialsFile = tempDir.resolve("api-credentials.json");
        Path settingsFile = tempDir.resolve("web-settings.json");
        var credentialsService = new CredentialsPersistenceService(credentialsFile);
        var settingsService = new SettingsPersistenceService(settingsFile);
        var settings = new com.coderhino.config.settings.WebSettings();
        settings.setDefaultModel("GPT-5.4");
        settingsService.save(settings);
        var credentials = new ApiCredentials();
        credentials.setDefaultProviderId("provider-1");
        credentials.setProviders(java.util.List.of(
            new ApiCredentials.ApiProvider("provider-1", "Anthropic", "secret-1", "https://api.anthropic.com", java.util.List.of(model("MiniMax-M2.1")))
        ));
        credentialsService.save(credentials);

        var registry = createRegistry(metadataDir, transcriptDir, credentialsService, settingsService);
        var session = registry.createSessionForProject("project-1").orElseThrow();
        var controller = new SessionController(registry, credentialsService);

        ResponseEntity<SessionDto> response = controller.getSession(session.getSessionId());

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("MiniMax-M2.1", session.getAppState().model());
        assertEquals("MiniMax-M2.1", response.getBody().getModel());
        assertEquals(java.util.List.of("MiniMax-M2.1"), response.getBody().getAvailableModels());
    }

    @Test
    void reloadSessionWithoutSavedModelUsesResolvedProviderModel(@TempDir Path tempDir) throws Exception {
        Path metadataDir = tempDir.resolve("metadata");
        Path transcriptDir = tempDir.resolve("transcripts");
        Path credentialsFile = tempDir.resolve("api-credentials.json");
        Path settingsFile = tempDir.resolve("web-settings.json");
        java.nio.file.Files.createDirectories(metadataDir);
        java.nio.file.Files.createDirectories(transcriptDir);
        var credentialsService = new CredentialsPersistenceService(credentialsFile);
        var settingsService = new SettingsPersistenceService(settingsFile);
        var settings = new com.coderhino.config.settings.WebSettings();
        settings.setDefaultModel("GPT-5.4");
        settingsService.save(settings);
        var credentials = new ApiCredentials();
        credentials.setDefaultProviderId("provider-1");
        credentials.setProviders(java.util.List.of(
            new ApiCredentials.ApiProvider("provider-1", "Anthropic", "secret-1", "https://api.anthropic.com", java.util.List.of(model("MiniMax-M2.1")))
        ));
        credentialsService.save(credentials);

        var metadata = new com.coderhino.web.session.SessionMetadata(
            "00000000-0000-0000-0000-000000000124",
            java.time.Instant.parse("2026-04-11T00:00:00Z"),
            null,
            "BYPASS",
            transcriptDir.toString(),
            "project-1",
            null,
            "Reloaded Session",
            "main",
            null,
            "default",
            com.coderhino.types.PermissionMode.BYPASS.name()
        );
        var objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        objectMapper.writerWithDefaultPrettyPrinter().writeValue(metadataDir.resolve(metadata.getSessionId() + ".json").toFile(), metadata);

        var registry = createRegistry(metadataDir, transcriptDir, credentialsService, settingsService);
        registry.reloadPersistedSessions();
        var controller = new SessionController(registry, credentialsService);

        ResponseEntity<SessionDto> response = controller.getSession(metadata.getSessionId());

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("provider-1", response.getBody().getProviderId());
        assertEquals("MiniMax-M2.1", response.getBody().getModel());
        assertEquals(java.util.List.of("MiniMax-M2.1"), response.getBody().getAvailableModels());
    }

    @Test
    void reloadSessionWithSavedModelPreservesExplicitOverride(@TempDir Path tempDir) throws Exception {
        Path metadataDir = tempDir.resolve("metadata");
        Path transcriptDir = tempDir.resolve("transcripts");
        Path credentialsFile = tempDir.resolve("api-credentials.json");
        Path settingsFile = tempDir.resolve("web-settings.json");
        java.nio.file.Files.createDirectories(metadataDir);
        java.nio.file.Files.createDirectories(transcriptDir);
        var credentialsService = new CredentialsPersistenceService(credentialsFile);
        var settingsService = new SettingsPersistenceService(settingsFile);
        var settings = new com.coderhino.config.settings.WebSettings();
        settings.setDefaultModel("GPT-5.4");
        settingsService.save(settings);
        var credentials = new ApiCredentials();
        credentials.setDefaultProviderId("provider-1");
        credentials.setProviders(java.util.List.of(
            new ApiCredentials.ApiProvider("provider-1", "Anthropic", "secret-1", "https://api.anthropic.com", java.util.List.of(model("MiniMax-M2.1")))
        ));
        credentialsService.save(credentials);

        var metadata = new com.coderhino.web.session.SessionMetadata(
            "00000000-0000-0000-0000-000000000125",
            java.time.Instant.parse("2026-04-11T00:00:00Z"),
            "custom-session-model",
            "BYPASS",
            transcriptDir.toString(),
            "project-1",
            null,
            "Reloaded Session Override",
            "main",
            null,
            "default",
            com.coderhino.types.PermissionMode.BYPASS.name()
        );
        var objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        objectMapper.writerWithDefaultPrettyPrinter().writeValue(metadataDir.resolve(metadata.getSessionId() + ".json").toFile(), metadata);

        var registry = createRegistry(metadataDir, transcriptDir, credentialsService, settingsService);
        registry.reloadPersistedSessions();
        var controller = new SessionController(registry, credentialsService);

        ResponseEntity<SessionDto> response = controller.getSession(metadata.getSessionId());

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("custom-session-model", response.getBody().getModel());
        assertEquals(java.util.List.of("custom-session-model", "MiniMax-M2.1"), response.getBody().getAvailableModels());
    }

    @Test
    void getSessionPreservesUnavailableProviderAndModel(@TempDir Path tempDir) {
        Path metadataDir = tempDir.resolve("metadata");
        Path transcriptDir = tempDir.resolve("transcripts");
        Path credentialsFile = tempDir.resolve("api-credentials.json");
        Path settingsFile = tempDir.resolve("web-settings.json");
        var credentialsService = new CredentialsPersistenceService(credentialsFile);
        var settingsService = new SettingsPersistenceService(settingsFile);
        var credentials = new ApiCredentials();
        credentials.setDefaultProviderId("provider-1");
        credentials.setProviders(java.util.List.of(
            new ApiCredentials.ApiProvider("provider-1", "Anthropic", "secret-1", "https://api.anthropic.com", java.util.List.of(model("MiniMax-M2.5")))
        ));
        credentialsService.save(credentials);

        var registry = createRegistry(metadataDir, transcriptDir, credentialsService, settingsService);
        var session = registry.createSessionForProject("project-1").orElseThrow();
        session.setProviderId("provider-missing");
        session.getBootstrapState().update(state -> state.withModel("legacy-model"));
        registry.persistSessionState(session);
        var controller = new SessionController(registry, credentialsService);

        ResponseEntity<SessionDto> response = controller.getSession(session.getSessionId());

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("provider-missing", response.getBody().getProviderId());
        assertEquals(java.util.List.of("legacy-model"), response.getBody().getAvailableModels());
        assertEquals("provider-missing", response.getBody().getAvailableProviders().get(0).getId());
        assertTrue(response.getBody().getAvailableProviders().get(0).isUnavailable());
    }

    @Test
    void getSessionContextReturnsSummaryAndRawAiHistory(@TempDir Path tempDir) {
        Path metadataDir = tempDir.resolve("metadata");
        Path transcriptDir = tempDir.resolve("transcripts");
        var registry = createRegistry(metadataDir, transcriptDir);
        var session = registry.createSessionForProject("project-1").orElseThrow();
        session.getBootstrapState().update(state -> state.withSessionRuntime(
            state.sessionRuntime()
                .appendRawAiHistory(new com.coderhino.state.SessionRuntime.RawAiHistoryEntry(java.time.Instant.parse("2026-04-07T10:01:00Z"), "request", "raw request"))
                .appendRawAiHistory(new com.coderhino.state.SessionRuntime.RawAiHistoryEntry(java.time.Instant.parse("2026-04-07T10:01:01Z"), "response", "raw response"))
        ));
        session.getBootstrapState().update(state -> state
            .addUsage(120, 45)
            .incrementToolUses());
        session.setProviderId("provider-1");
        session.setName("Context Session");

        var controller = new SessionController(registry);
        ResponseEntity<SessionContextDto> response = controller.getSessionContext(session.getSessionId());

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertNotNull(response.getBody().getSummary());
        assertEquals(session.getSessionId(), response.getBody().getSummary().getSessionId());
        assertEquals("Context Session", response.getBody().getSummary().getName());
        assertEquals("provider-1", response.getBody().getSummary().getProviderId());
        assertNotNull(response.getBody().getSummary().getSessionTotals());
        assertNull(response.getBody().getSummary().getCurrentUsage());
        assertEquals(Long.valueOf(120), response.getBody().getSummary().getSessionTotals().getInputTokens());
        assertEquals(Long.valueOf(45), response.getBody().getSummary().getSessionTotals().getOutputTokens());
        assertEquals(Integer.valueOf(1), response.getBody().getSummary().getSessionTotals().getToolUses());
        assertEquals(2, response.getBody().getRawAiHistory().size());
        assertEquals("request", response.getBody().getRawAiHistory().get(0).getDirection());
        assertEquals("raw request", response.getBody().getRawAiHistory().get(0).getContent());
        assertEquals("response", response.getBody().getRawAiHistory().get(1).getDirection());
        assertEquals(Long.valueOf(165), response.getBody().getSummary().getSessionTotals().getContextLength());
    }

    @Test
    void getSessionContextReturnsZeroMetricsAndEmptyRawAiHistoryWhenNoEntries(@TempDir Path tempDir) {
        Path metadataDir = tempDir.resolve("metadata");
        Path transcriptDir = tempDir.resolve("transcripts");
        var registry = createRegistry(metadataDir, transcriptDir);
        var session = registry.createSessionForProject("project-1").orElseThrow();
        session.getBootstrapState().addMessage(new Message.UserMessage("only user so far"));

        var controller = new SessionController(registry);
        ResponseEntity<SessionContextDto> response = controller.getSessionContext(session.getSessionId());

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertNotNull(response.getBody().getSummary());
        assertNotNull(response.getBody().getSummary().getSessionTotals());
        assertEquals(Long.valueOf(0), response.getBody().getSummary().getSessionTotals().getInputTokens());
        assertEquals(Long.valueOf(0), response.getBody().getSummary().getSessionTotals().getOutputTokens());
        assertEquals(Long.valueOf(0), response.getBody().getSummary().getSessionTotals().getCacheReadTokens());
        assertEquals(Long.valueOf(0), response.getBody().getSummary().getSessionTotals().getCacheWriteTokens());
        assertEquals(Integer.valueOf(0), response.getBody().getSummary().getSessionTotals().getToolUses());
        assertEquals(Long.valueOf(0), response.getBody().getSummary().getSessionTotals().getContextLength());
        assertTrue(response.getBody().getRawAiHistory().isEmpty());
    }

    @Test
    void getSessionContextPreservesPersistedUsageMetricsAfterReload(@TempDir Path tempDir) {
        Path metadataDir = tempDir.resolve("metadata");
        Path transcriptDir = tempDir.resolve("transcripts");
        var registry = createRegistry(metadataDir, transcriptDir);
        var created = registry.createSessionForProject("project-1").orElseThrow();
        created.getBootstrapState().update(state -> state.withUsageTotals(42, 7, 5, 3, 2));
        registry.persistSessionState(created);

        var reloadedRegistry = createRegistry(metadataDir, transcriptDir);
        reloadedRegistry.reloadPersistedSessions();
        var controller = new SessionController(reloadedRegistry);

        ResponseEntity<SessionContextDto> response = controller.getSessionContext(created.getSessionId());

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertNotNull(response.getBody().getSummary().getSessionTotals());
        assertEquals(Long.valueOf(42), response.getBody().getSummary().getSessionTotals().getInputTokens());
        assertEquals(Long.valueOf(7), response.getBody().getSummary().getSessionTotals().getOutputTokens());
        assertEquals(Long.valueOf(5), response.getBody().getSummary().getSessionTotals().getCacheReadTokens());
        assertEquals(Long.valueOf(3), response.getBody().getSummary().getSessionTotals().getCacheWriteTokens());
        assertEquals(Integer.valueOf(2), response.getBody().getSummary().getSessionTotals().getToolUses());
        assertEquals(Long.valueOf(57), response.getBody().getSummary().getSessionTotals().getContextLength());
    }

    @Test
    void getSessionContextKeepsCurrentUsageScopedPerSession(@TempDir Path tempDir) {
        Path metadataDir = tempDir.resolve("metadata");
        Path transcriptDir = tempDir.resolve("transcripts");
        var registry = createRegistry(metadataDir, transcriptDir);
        var sessionA = registry.createSessionForProject("project-1").orElseThrow();
        var sessionB = registry.createSessionForProject("project-1").orElseThrow();

        sessionA.getActiveRun().set(true);
        sessionA.setActiveRunId("run-a");
        sessionA.setCurrentRunStatus(com.coderhino.web.dto.RunDto.RunStatus.RUNNING);
        sessionA.startActiveRunReplay("run-a");
        sessionA.recordReplayUsage("run-a", 11, 7, 3, 2, 1, 23);

        sessionB.getBootstrapState().update(state -> state.withCurrentUsage(new com.coderhino.state.AppState.CurrentUsage(99, 88, 77, 66, 5)));

        var controller = new SessionController(registry);
        ResponseEntity<SessionContextDto> responseA = controller.getSessionContext(sessionA.getSessionId());
        ResponseEntity<SessionContextDto> responseB = controller.getSessionContext(sessionB.getSessionId());

        assertEquals(HttpStatus.OK, responseA.getStatusCode());
        assertEquals(HttpStatus.OK, responseB.getStatusCode());
        assertNotNull(responseA.getBody());
        assertNotNull(responseB.getBody());
        assertNotNull(responseA.getBody().getSummary().getCurrentUsage());
        assertEquals(Long.valueOf(11), responseA.getBody().getSummary().getCurrentUsage().getInputTokens());
        assertEquals(Long.valueOf(7), responseA.getBody().getSummary().getCurrentUsage().getOutputTokens());
        assertEquals(Integer.valueOf(1), responseA.getBody().getSummary().getCurrentUsage().getToolUses());
        assertEquals(Long.valueOf(23), responseA.getBody().getSummary().getCurrentUsage().getContextLength());
        assertNull(responseB.getBody().getSummary().getCurrentUsage());
    }

    @Test
    void getSessionContextReturns404ForMissingSession() {
        var registry = createRegistry();
        var controller = new SessionController(registry);

        ResponseEntity<SessionContextDto> response = controller.getSessionContext("missing");

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertNull(response.getBody());
    }

    @Test
    void getSessionGitStatusReturnsStructuredGitData(@TempDir Path tempDir) {
        Path metadataDir = tempDir.resolve("metadata");
        Path transcriptDir = tempDir.resolve("transcripts");
        var registry = createRegistry(metadataDir, transcriptDir);
        var session = registry.createSessionForProject("project-1").orElseThrow();
        var gitStatusService = new SessionGitStatusService() {
            @Override
            public SessionGitStatusDto getStatus(Path worktreePath) {
                assertEquals(transcriptDir.toString(), worktreePath.toString());
                return new SessionGitStatusDto(
                    java.util.List.of(SessionGitStatusDto.GitEntry.tracked("src/App.tsx", "modified")),
                    java.util.List.of(SessionGitStatusDto.GitEntry.unversioned("notes/todo.md"))
                );
            }
        };
        var controller = new SessionController(registry, new CredentialsPersistenceService(), gitStatusService);

        ResponseEntity<?> response = controller.getSessionGitStatus(session.getSessionId());

        assertEquals(HttpStatus.OK, response.getStatusCode());
        var body = assertInstanceOf(SessionGitStatusDto.class, response.getBody());
        assertEquals(1, body.getTrackedChanges().size());
        assertEquals("tracked", body.getTrackedChanges().get(0).getKind());
        assertEquals("src/App.tsx", body.getTrackedChanges().get(0).getPath());
        assertEquals("modified", body.getTrackedChanges().get(0).getStatus());
        assertEquals(1, body.getUnversionedFiles().size());
        assertEquals("unversioned", body.getUnversionedFiles().get(0).getKind());
        assertEquals("notes/todo.md", body.getUnversionedFiles().get(0).getPath());
    }

    @Test
    void getSessionGitStatusReturnsBadRequestWhenGitStatusFails(@TempDir Path tempDir) {
        Path metadataDir = tempDir.resolve("metadata");
        Path transcriptDir = tempDir.resolve("transcripts");
        var registry = createRegistry(metadataDir, transcriptDir);
        var session = registry.createSessionForProject("project-1").orElseThrow();
        var gitStatusService = new SessionGitStatusService() {
            @Override
            public SessionGitStatusDto getStatus(Path worktreePath) {
                throw new SessionGitStatusException("Resolved worktree is not a git repository.");
            }
        };
        var controller = new SessionController(registry, new CredentialsPersistenceService(), gitStatusService);

        ResponseEntity<?> response = controller.getSessionGitStatus(session.getSessionId());

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        var body = assertInstanceOf(ErrorResponse.class, response.getBody());
        assertEquals("Resolved worktree is not a git repository.", body.getError());
    }

    @Test
    void getSessionGitStatusReturns404ForMissingSession() {
        var registry = createRegistry();
        var controller = new SessionController(registry);

        ResponseEntity<?> response = controller.getSessionGitStatus("missing");

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertNull(response.getBody());
    }

    @Test
    void getSessionGitDiffReturnsStructuredGitDiffData(@TempDir Path tempDir) {
        Path metadataDir = tempDir.resolve("metadata");
        Path transcriptDir = tempDir.resolve("transcripts");
        var registry = createRegistry(metadataDir, transcriptDir);
        var session = registry.createSessionForProject("project-1").orElseThrow();
        var gitStatusService = new SessionGitStatusService() {
            @Override
            public SessionGitDiffDto getDiff(Path worktreePath, String filePath) {
                assertEquals(transcriptDir.toString(), worktreePath.toString());
                assertEquals("src/App.tsx", filePath);
                return new SessionGitDiffDto("tracked", "src/App.tsx", "diff --git a/src/App.tsx b/src/App.tsx");
            }
        };
        var controller = new SessionController(registry, new CredentialsPersistenceService(), gitStatusService);

        ResponseEntity<?> response = controller.getSessionGitDiff(session.getSessionId(), "src/App.tsx");

        assertEquals(HttpStatus.OK, response.getStatusCode());
        var body = assertInstanceOf(SessionGitDiffDto.class, response.getBody());
        assertEquals("tracked", body.getKind());
        assertEquals("src/App.tsx", body.getPath());
        assertEquals("diff --git a/src/App.tsx b/src/App.tsx", body.getDiff());
    }

    @Test
    void getSessionGitDiffReturnsBadRequestWhenGitDiffFails(@TempDir Path tempDir) {
        Path metadataDir = tempDir.resolve("metadata");
        Path transcriptDir = tempDir.resolve("transcripts");
        var registry = createRegistry(metadataDir, transcriptDir);
        var session = registry.createSessionForProject("project-1").orElseThrow();
        var gitStatusService = new SessionGitStatusService() {
            @Override
            public SessionGitDiffDto getDiff(Path worktreePath, String filePath) {
                throw new SessionGitStatusException("File is not a tracked or unversioned change in the session worktree.");
            }
        };
        var controller = new SessionController(registry, new CredentialsPersistenceService(), gitStatusService);

        ResponseEntity<?> response = controller.getSessionGitDiff(session.getSessionId(), "src/App.tsx");

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        var body = assertInstanceOf(ErrorResponse.class, response.getBody());
        assertEquals("File is not a tracked or unversioned change in the session worktree.", body.getError());
    }

    @Test
    void getSessionGitDiffReturns404ForMissingSession() {
        var registry = createRegistry();
        var controller = new SessionController(registry);

        ResponseEntity<?> response = controller.getSessionGitDiff("missing", "src/App.tsx");

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertNull(response.getBody());
    }
}
