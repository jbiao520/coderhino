package com.coderhino.web.session;

import com.coderhino.state.SessionStore;
import com.coderhino.types.PermissionMode;
import com.coderhino.web.credentials.CredentialsPersistenceService;
import com.coderhino.web.git.GitBranchResolver;
import com.coderhino.web.events.SessionEventBus;
import com.coderhino.web.project.ProjectPersistenceService;
import com.coderhino.web.settings.SettingsPersistenceService;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class WebSessionRegistry {

    private static final Logger log = LoggerFactory.getLogger(WebSessionRegistry.class);

    private final ConcurrentHashMap<String, WebSession> sessions = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, String> sessionProjectIds = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, String> sessionWorktreeIds = new ConcurrentHashMap<>();
    private final SessionPersistenceService persistenceService;
    private final SessionStore sessionStore;
    private final SessionEventBus eventBus;
    private final ProjectPersistenceService projectService;
    private final CredentialsPersistenceService credentialsService;
    private final SettingsPersistenceService settingsService;

    public WebSessionRegistry(SessionPersistenceService persistenceService, SessionStore sessionStore, SessionEventBus eventBus, ProjectPersistenceService projectService) {
        this(persistenceService, sessionStore, eventBus, projectService, new CredentialsPersistenceService(), new SettingsPersistenceService());
    }

    @Autowired
    public WebSessionRegistry(SessionPersistenceService persistenceService, SessionStore sessionStore, SessionEventBus eventBus,
                              ProjectPersistenceService projectService, CredentialsPersistenceService credentialsService,
                              SettingsPersistenceService settingsService) {
        this.persistenceService = persistenceService;
        this.sessionStore = sessionStore;
        this.eventBus = eventBus;
        this.projectService = projectService;
        this.credentialsService = credentialsService;
        this.settingsService = settingsService;
    }

    @PostConstruct
    public void reloadPersistedSessions() {
        var metadataList = persistenceService.loadAll();
        for (var metadata : metadataList) {
            var projectId = metadata.getProjectId();
            if (projectId == null || projectId.isBlank()) {
                persistenceService.delete(metadata.getSessionId());
                log.warn("Dropping orphaned persisted session {} with missing projectId", metadata.getSessionId());
                continue;
            }
            var resolvedWorktree = projectService.resolveWorktree(projectId, metadata.getWorktreeId(), metadata.getCwd());
            if (resolvedWorktree.isEmpty()) {
                persistenceService.delete(metadata.getSessionId());
                log.warn("Dropping orphaned persisted session {} with unknown project/worktree", metadata.getSessionId());
                continue;
            }
            if (!sessions.containsKey(metadata.getSessionId())) {
                var cwd = Path.of(resolvedWorktree.get().getPath());
                var runtime = loadSessionRuntime(metadata.getSessionId(), cwd);
                var session = WebSession.create(metadata.getSessionId(), cwd, runtime);
                session.attachPersistence(sessionStore);
                if (metadata.getName() != null) {
                    session.setName(metadata.getName());
                }
                if (metadata.getBranch() != null) {
                    session.setBranch(metadata.getBranch());
                } else {
                    session.setBranch("Unknown Branch");
                }
                var providerId = metadata.getProviderId();
                if (providerId == null || providerId.isBlank()) {
                    providerId = resolveDefaultProviderId();
                }
                session.setProviderId(providerId);
                if (metadata.getModel() != null && !metadata.getModel().isBlank()) {
                    session.getBootstrapState().update(state -> state.withModel(metadata.getModel().trim()));
                } else {
                    applyDefaultProviderSelection(session);
                }
                session.getBootstrapState().update(state -> state.withUsageTotals(
                    metadata.getTotalInputTokens() != null ? metadata.getTotalInputTokens() : 0L,
                    metadata.getTotalOutputTokens() != null ? metadata.getTotalOutputTokens() : 0L,
                    metadata.getTotalCacheReadTokens() != null ? metadata.getTotalCacheReadTokens() : 0L,
                    metadata.getTotalCacheWriteTokens() != null ? metadata.getTotalCacheWriteTokens() : 0L,
                    metadata.getTotalToolUses() != null ? metadata.getTotalToolUses() : 0
                ));
                if (metadata.getPermissionMode() != null && !metadata.getPermissionMode().isBlank()) {
                    try {
                        session.getBootstrapState().update(state -> state.withPermissionMode(PermissionMode.valueOf(metadata.getPermissionMode().trim().toUpperCase())));
                    } catch (IllegalArgumentException ignored) {
                    }
                }
                if (metadata.getNormalPermissionMode() != null && !metadata.getNormalPermissionMode().isBlank()) {
                    try {
                        session.setNormalPermissionMode(PermissionMode.valueOf(metadata.getNormalPermissionMode().trim().toUpperCase()));
                    } catch (IllegalArgumentException ignored) {
                    }
                } else {
                    session.setNormalPermissionMode(session.getAppState().permissionMode() == PermissionMode.PLAN
                        ? PermissionMode.BYPASS
                        : session.getAppState().permissionMode());
                }
                session.setModelMode(metadata.getModelMode());
                sessions.put(session.getSessionId(), session);
                sessionProjectIds.put(session.getSessionId(), projectId);
                sessionWorktreeIds.put(session.getSessionId(), resolvedWorktree.get().getId());
                if (metadata.getWorktreeId() == null || metadata.getWorktreeId().isBlank()
                    || metadata.getProviderId() == null || metadata.getProviderId().isBlank()) {
                    persistenceService.persist(session, projectId, resolvedWorktree.get().getId());
                }
                log.debug("Reloaded persisted session: {}", session.getSessionId());
            }
        }
        log.info("WebSessionRegistry initialized with {} persisted sessions", metadataList.size());
    }

    public WebSession create() {
        var sessionId = UUID.randomUUID().toString();
        var session = WebSession.create(sessionId);
        applyDefaultProviderSelection(session);
        session.setBranch(GitBranchResolver.resolve(Path.of("").toAbsolutePath().normalize()));
        session.attachPersistence(sessionStore);
        sessions.put(sessionId, session);
        persistenceService.persist(session);
        log.debug("Created new session: {}", sessionId);
        return session;
    }

    /**
     * Creates a new session scoped to a specific project.
     * Looks up the project's cwd and creates the session with that working directory.
     * @param projectId the project to scope the session to
     * @return the new session, or empty if project not found
     */
    public Optional<WebSession> createSessionForProject(String projectId) {
        return createSessionForProject(projectId, null);
    }

    public Optional<WebSession> createSessionForProject(String projectId, String worktreeId) {
        var projectOpt = projectService.find(projectId);
        var worktreeOpt = projectService.resolveWorktree(projectId, worktreeId, null);
        if (projectOpt.isEmpty() || worktreeOpt.isEmpty()) {
            log.warn("Cannot create session for unknown project/worktree: {} / {}", projectId, worktreeId);
            return Optional.empty();
        }
        var project = projectOpt.get();
        var worktree = worktreeOpt.get();
        var sessionId = UUID.randomUUID().toString();
        var cwd = Path.of(worktree.getPath());
        var session = WebSession.create(sessionId, cwd);
        applyDefaultProviderSelection(session);
        session.setBranch(GitBranchResolver.resolve(cwd));
        session.setNormalPermissionMode(session.getAppState().permissionMode());
        session.attachPersistence(sessionStore);
        sessions.put(sessionId, session);
        sessionProjectIds.put(sessionId, projectId);
        sessionWorktreeIds.put(sessionId, worktree.getId());
        persistenceService.persist(session, projectId, worktree.getId());
        log.debug("Created new session {} for project {} ({}) in worktree {}", sessionId, project.getName(), projectId, worktree.getId());
        return Optional.of(session);
    }

    public Optional<String> getProjectIdForSession(String sessionId) {
        return Optional.ofNullable(sessionProjectIds.get(sessionId));
    }

    public Optional<String> getWorktreeIdForSession(String sessionId) {
        return Optional.ofNullable(sessionWorktreeIds.get(sessionId));
    }

    public Optional<com.coderhino.web.project.Worktree> getWorktreeForSession(String sessionId) {
        var projectId = sessionProjectIds.get(sessionId);
        var worktreeId = sessionWorktreeIds.get(sessionId);
        if (projectId == null) {
            return Optional.empty();
        }
        return projectService.resolveWorktree(projectId, worktreeId, sessions.get(sessionId) != null ? sessions.get(sessionId).getAppState().cwd() : null);
    }

    public Optional<WebSession> find(String sessionId) {
        return Optional.ofNullable(sessions.get(sessionId));
    }

    public Collection<WebSession> listAll() {
        return List.copyOf(sessions.values());
    }

    public List<WebSession> listByProject(String projectId) {
        return sessions.values().stream()
            .filter(s -> projectId.equals(sessionProjectIds.get(s.getSessionId())))
            .toList();
    }

    public int size() {
        return sessions.size();
    }

    public boolean remove(String sessionId) {
        var removed = sessions.remove(sessionId);
        sessionProjectIds.remove(sessionId);
        sessionWorktreeIds.remove(sessionId);
        persistenceService.delete(sessionId);
        if (removed == null) {
            return false;
        }
        sessionStore.deleteSession(removed.getAppState().sessionRuntime().sessionId(), removed.getAppState().cwd());
        return true;
    }

    public Optional<WebSession> updateName(String sessionId, String name) {
        return find(sessionId).map(session -> {
            session.setName(name);
            persistSessionState(session);
            return session;
        });
    }

    public Optional<WebSession> updateProviderSelection(String sessionId, String providerId) {
        return find(sessionId).map(session -> {
            session.setProviderId(providerId);
            persistSessionState(session);
            return session;
        });
    }

    public void persistSessionState(WebSession session) {
        if (session == null) {
            return;
        }
        var sessionId = session.getSessionId();
        var projectId = sessionProjectIds.get(sessionId);
        var worktreeId = sessionWorktreeIds.get(sessionId);
        persistenceService.persist(session, projectId, worktreeId);
    }

    public SessionStore getSessionStore() {
        return sessionStore;
    }

    public void autoNameSession(WebSession session) {
        if (session.getName() != null) {
            return;
        }
        var messages = session.getAppState().messages();
        var firstUserMsg = messages.stream()
            .filter(m -> m instanceof com.coderhino.types.Message.UserMessage)
            .map(com.coderhino.types.Message::content)
            .findFirst()
            .orElse(null);
        if (firstUserMsg == null) {
            return;
        }
        var cleaned = firstUserMsg.replaceAll("[\\r\\n]+", " ").trim();
        var name = cleaned.length() > 80
            ? cleaned.substring(0, 80) + "…"
            : cleaned;
        session.setName(name);
        persistSessionState(session);
    }

    public SessionEventBus getEventBus() {
        return eventBus;
    }

    public CredentialsPersistenceService getCredentialsService() {
        return credentialsService;
    }

    private void applyDefaultProviderSelection(WebSession session) {
        var credentials = credentialsService.load();
        var defaultProvider = credentials.getDefaultProvider();
        session.setProviderId(defaultProvider != null ? defaultProvider.getId() : null);
        var model = resolveInitialModel(defaultProvider);
        if (model != null) {
            session.getBootstrapState().update(state -> state.withModel(model));
        }
    }

    private String resolveDefaultProviderId() {
        var defaultProvider = credentialsService.load().getDefaultProvider();
        return defaultProvider != null ? defaultProvider.getId() : null;
    }

    private String resolveInitialModel(com.coderhino.web.credentials.ApiCredentials.ApiProvider provider) {
        if (provider != null && provider.getModels() != null && !provider.getModels().isEmpty()) {
            return provider.getModels().get(0).getId();
        }
        var settingsModel = settingsService.load().getDefaultModel();
        if (settingsModel != null && !settingsModel.isBlank()) {
            return settingsModel.trim();
        }
        return null;
    }

    private com.coderhino.state.SessionRuntime loadSessionRuntime(String sessionId, Path cwd) {
        try {
            return sessionStore.loadSession(UUID.fromString(sessionId), cwd.toAbsolutePath().normalize().toString());
        } catch (IllegalArgumentException e) {
            log.warn("Session {} is not a valid UUID; restoring without transcript", sessionId);
            return new com.coderhino.state.SessionRuntime(UUID.nameUUIDFromBytes(sessionId.getBytes(java.nio.charset.StandardCharsets.UTF_8)), null, null, java.util.List.of(), java.util.List.of(), java.util.List.of());
        } catch (IllegalStateException e) {
            log.warn("Failed to restore transcript for session {}: {}", sessionId, e.getMessage());
            return new com.coderhino.state.SessionRuntime(UUID.fromString(sessionId), null, null, java.util.List.of(), java.util.List.of(), java.util.List.of());
        }
    }
}
