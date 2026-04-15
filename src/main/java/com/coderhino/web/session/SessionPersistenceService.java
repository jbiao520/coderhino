package com.coderhino.web.session;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

@Service
public class SessionPersistenceService {

    private static final Logger log = LoggerFactory.getLogger(SessionPersistenceService.class);
    private static final String SESSIONS_DIR = ".coderhino/web-sessions";

    private final ObjectMapper objectMapper;
    private final Path sessionsDir;
    private final int maxPersisted;

    @Autowired
    public SessionPersistenceService(@Value("${web.sessions.max-persisted:100}") int maxPersisted) {
        this(Path.of("").toAbsolutePath().normalize().resolve(SESSIONS_DIR), maxPersisted);
    }

    public SessionPersistenceService(Path sessionsDir) {
        this(sessionsDir, 100);
    }

    public SessionPersistenceService(Path sessionsDir, int maxPersisted) {
        this.sessionsDir = sessionsDir;
        this.maxPersisted = maxPersisted;
        this.objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    public void persist(WebSession session) {
        persist(session, null);
    }

    public void persist(WebSession session, String projectId) {
        persist(session, projectId, null);
    }

    public void persist(WebSession session, String projectId, String worktreeId) {
        ensureDirectory();
        enforceRetentionCap();
        var metadata = new SessionMetadata(
            session.getSessionId(),
            session.getCreatedAt(),
            session.getAppState().model(),
            session.getAppState().permissionMode().name(),
            session.getAppState().cwd(),
            projectId,
            worktreeId,
            session.getName(),
            session.getBranch(),
            session.getProviderId(),
            session.getModelMode(),
            session.getNormalPermissionMode().name(),
            session.getAppState().totalInputTokens(),
            session.getAppState().totalOutputTokens(),
            session.getAppState().totalCacheReadTokens(),
            session.getAppState().totalCacheWriteTokens(),
            session.getAppState().totalToolUses()
        );
        var file = sessionsDir.resolve(session.getSessionId() + ".json");
        try {
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(file.toFile(), metadata);
        } catch (IOException e) {
            log.warn("Failed to persist session {}: {}", session.getSessionId(), e.getMessage());
        }
    }

    private void enforceRetentionCap() {
        List<SessionMetadata> existing = loadAll();
        if (existing.size() < maxPersisted) {
            return;
        }
        existing.stream()
            .sorted(Comparator.comparing(SessionMetadata::getCreatedAt))
            .limit(existing.size() - maxPersisted + 1)
            .forEach(m -> delete(m.getSessionId()));
    }

    public List<SessionMetadata> loadAll() {
        ensureDirectory();
        var result = new ArrayList<SessionMetadata>();
        try (Stream<Path> files = Files.list(sessionsDir)) {
            files.filter(p -> p.toString().endsWith(".json"))
                .forEach(file -> {
                    try {
                        var metadata = objectMapper.readValue(file.toFile(), SessionMetadata.class);
                        result.add(metadata);
                    } catch (IOException e) {
                        log.warn("Failed to load session from {}: {}", file, e.getMessage());
                    }
                });
        } catch (IOException e) {
            log.warn("Failed to list sessions directory {}: {}", sessionsDir, e.getMessage());
        }
        return result;
    }

    public void delete(String sessionId) {
        var file = sessionsDir.resolve(sessionId + ".json");
        try {
            Files.deleteIfExists(file);
        } catch (IOException e) {
            log.warn("Failed to delete session file for {}: {}", sessionId, e.getMessage());
        }
    }

    @EventListener(ContextClosedEvent.class)
    public void onContextClosed(ContextClosedEvent event) {
        int count = loadAll().size();
        log.info("SessionPersistenceService flush on shutdown — {} session(s) on disk", count);
    }

    public Path getSessionsDir() {
        return sessionsDir;
    }

    public int getMaxPersisted() {
        return maxPersisted;
    }

    private void ensureDirectory() {
        if (!Files.exists(sessionsDir)) {
            try {
                Files.createDirectories(sessionsDir);
            } catch (IOException e) {
                log.error("Cannot create sessions directory {}: {}", sessionsDir, e.getMessage());
                throw new RuntimeException("Cannot create sessions directory: " + sessionsDir, e);
            }
        }
    }
}
