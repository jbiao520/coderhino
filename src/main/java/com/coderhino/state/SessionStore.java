package com.coderhino.state;

import com.coderhino.types.Message;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.core.type.TypeReference;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

public final class SessionStore {
    private final ObjectMapper objectMapper;
    private final Path sessionsRoot;

    public SessionStore() {
        this(defaultObjectMapper(), defaultSessionsRoot());
    }

    public SessionStore(ObjectMapper objectMapper, Path sessionsRoot) {
        this.objectMapper = objectMapper;
        this.sessionsRoot = sessionsRoot;
    }

    public Message.Envelope recordMessage(AppState state, Message message) {
        var runtime = state.sessionRuntime();
        var envelope = new Message.Envelope(UUID.randomUUID(), runtime.lastMessageId(), Instant.now(), message);
        appendRecord(state, SessionRecord.forMessage(runtime.sessionId(), state.cwd(), envelope));
        return envelope;
    }

    public void saveCustomTitle(AppState state, String title) {
        appendRecord(state, SessionRecord.forCustomTitle(state.sessionRuntime().sessionId(), state.cwd(), title));
    }

    public void appendCompletedTurnActivity(AppState state, SessionRuntime.CompletedTurnActivity activity) {
        appendRecord(state, completedTurnActivityRecord(state, activity));
    }

    public void replaceTranscript(AppState state, List<Message.Envelope> envelopes) {
        var sessionFile = transcriptPath(state.cwd(), state.sessionRuntime().sessionId());
        try {
            Files.createDirectories(sessionFile.getParent());
            var lines = new ArrayList<String>();
            if (state.sessionRuntime().customTitle() != null && !state.sessionRuntime().customTitle().isBlank()) {
                lines.add(objectMapper.writeValueAsString(
                    SessionRecord.forCustomTitle(state.sessionRuntime().sessionId(), state.cwd(), state.sessionRuntime().customTitle())
                ));
            }
            for (var envelope : envelopes) {
                lines.add(objectMapper.writeValueAsString(SessionRecord.forMessage(state.sessionRuntime().sessionId(), state.cwd(), envelope)));
            }
            for (var activity : state.sessionRuntime().replaceTranscript(envelopes).completedTurnActivities()) {
                lines.add(objectMapper.writeValueAsString(completedTurnActivityRecord(state, activity)));
            }
            var content = lines.isEmpty()
                ? ""
                : String.join(System.lineSeparator(), lines) + System.lineSeparator();
            Files.writeString(
                sessionFile,
                content,
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING,
                StandardOpenOption.WRITE
            );
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to replace session transcript: %s".formatted(exception.getMessage()), exception);
        }
    }

    public SessionRuntime loadSession(UUID sessionId, String cwd) {
        var sessionFile = transcriptPath(cwd, sessionId);
        if (!Files.exists(sessionFile)) {
            return new SessionRuntime(sessionId, null, null, List.of(), List.of(), List.of());
        }

        try {
            var transcript = new ArrayList<Message.Envelope>();
            var completedTurnActivities = new ArrayList<SessionRuntime.CompletedTurnActivity>();
            String customTitle = null;
            for (String line : Files.readAllLines(sessionFile, StandardCharsets.UTF_8)) {
                if (line.isBlank()) {
                    continue;
                }
                var record = objectMapper.readValue(line, SessionRecord.class);
                if ("custom-title".equals(record.entryType())) {
                    customTitle = record.customTitle();
                    continue;
                }
                if ("completed-turn-activity".equals(record.entryType())) {
                    var activity = materializeCompletedTurnActivity(record);
                    if (activity != null) {
                        completedTurnActivities.add(activity);
                    }
                    continue;
                }
                if (!"message".equals(record.entryType())) {
                    continue;
                }
                transcript.add(new Message.Envelope(record.uuid(), record.parentUuid(), record.timestamp(), materializeMessage(record)));
            }

            transcript.sort(Comparator.comparing(Message.Envelope::timestamp));
            UUID lastMessageId = transcript.isEmpty() ? null : transcript.get(transcript.size() - 1).uuid();
            var transcriptIds = transcript.stream().map(Message.Envelope::uuid).collect(java.util.stream.Collectors.toSet());
            var retainedActivities = completedTurnActivities.stream()
                .filter(activity -> transcriptIds.contains(activity.assistantMessageId()))
                .toList();
            return new SessionRuntime(sessionId, lastMessageId, customTitle, transcript, List.of(), retainedActivities);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to load session %s: %s".formatted(sessionId, exception.getMessage()), exception);
        }
    }

    public boolean sessionExists(UUID sessionId, String cwd) {
        return Files.exists(transcriptPath(cwd, sessionId));
    }

    public int transcriptSize(UUID sessionId, String cwd) {
        var sessionFile = transcriptPath(cwd, sessionId);
        if (!Files.exists(sessionFile)) {
            return 0;
        }
        try {
            int count = 0;
            for (String line : Files.readAllLines(sessionFile, StandardCharsets.UTF_8)) {
                if (line.isBlank()) {
                    continue;
                }
                var record = objectMapper.readValue(line, SessionRecord.class);
                if ("message".equals(record.entryType())) {
                    count++;
                }
            }
            return count;
        } catch (IOException exception) {
            return 0;
        }
    }

    public void deleteSession(UUID sessionId, String cwd) {
        var sessionFile = transcriptPath(cwd, sessionId);
        try {
            Files.deleteIfExists(sessionFile);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to delete session %s: %s".formatted(sessionId, exception.getMessage()), exception);
        }
    }

    public List<SessionSummary> listSessions(String cwd) {
        var projectDir = projectDirectory(cwd);
        if (!Files.exists(projectDir)) {
            return List.of();
        }

        try (var stream = Files.list(projectDir)) {
            return stream
                .filter(path -> path.getFileName().toString().endsWith(".jsonl"))
                .sorted(Comparator.reverseOrder())
                .map(path -> summarize(cwd, path))
                .filter(java.util.Objects::nonNull)
                .toList();
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to list sessions: %s".formatted(exception.getMessage()), exception);
        }
    }

    private SessionSummary summarize(String cwd, Path path) {
        try {
            String firstPrompt = null;
            String customTitle = null;
            int messageCount = 0;
            Instant updatedAt = Instant.EPOCH;
            UUID sessionId = null;

            for (String line : Files.readAllLines(path, StandardCharsets.UTF_8)) {
                if (line.isBlank()) {
                    continue;
                }
                var record = objectMapper.readValue(line, SessionRecord.class);
                sessionId = record.sessionId();
                updatedAt = record.timestamp().isAfter(updatedAt) ? record.timestamp() : updatedAt;
                if ("custom-title".equals(record.entryType())) {
                    customTitle = record.customTitle();
                }
                if ("message".equals(record.entryType())) {
                    messageCount++;
                    if (firstPrompt == null && "user".equals(record.messageType())) {
                        firstPrompt = record.content();
                    }
                }
            }

            if (sessionId == null) {
                return null;
            }
            return new SessionSummary(sessionId, customTitle, firstPrompt, messageCount, updatedAt, transcriptPath(cwd, sessionId));
        } catch (IOException exception) {
            return null;
        }
    }

    private void appendRecord(AppState state, SessionRecord record) {
        var sessionFile = transcriptPath(state.cwd(), state.sessionRuntime().sessionId());
        try {
            Files.createDirectories(sessionFile.getParent());
            Files.writeString(
                sessionFile,
                objectMapper.writeValueAsString(record) + System.lineSeparator(),
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE,
                StandardOpenOption.APPEND
            );
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to persist session record: %s".formatted(exception.getMessage()), exception);
        }
    }

    private SessionRecord completedTurnActivityRecord(AppState state, SessionRuntime.CompletedTurnActivity activity) {
        try {
            return new SessionRecord(
                "completed-turn-activity",
                state.sessionRuntime().sessionId(),
                UUID.randomUUID(),
                null,
                Instant.now(),
                null,
                null,
                null,
                null,
                null,
                activity.assistantMessageId().toString(),
                objectMapper.writeValueAsString(activity.transcript()),
                activity.fileSummary() == null ? null : objectMapper.writeValueAsString(activity.fileSummary()),
                null,
                state.cwd()
            );
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to serialize completed turn activity: %s".formatted(exception.getMessage()), exception);
        }
    }

    public List<Message.Envelope> rewrapMessages(AppState state, List<Message> messages) {
        var runtime = state.sessionRuntime();
        var envelopes = new ArrayList<Message.Envelope>(messages.size());
        UUID parentId = null;
        for (var existing : runtime.transcript()) {
            if (envelopes.size() >= messages.size()) {
                break;
            }
            if (Objects.equals(existing.message(), messages.get(envelopes.size()))) {
                envelopes.add(existing);
                parentId = existing.uuid();
                continue;
            }
            break;
        }

        Instant timestamp = runtime.transcript().isEmpty()
            ? Instant.now()
            : runtime.transcript().get(runtime.transcript().size() - 1).timestamp();
        for (int i = envelopes.size(); i < messages.size(); i++) {
            timestamp = timestamp.plusMillis(1);
            var envelope = new Message.Envelope(UUID.randomUUID(), parentId, timestamp, messages.get(i));
            envelopes.add(envelope);
            parentId = envelope.uuid();
        }
        return List.copyOf(envelopes);
    }

    private Message materializeMessage(SessionRecord record) {
        return switch (record.messageType()) {
            case "user" -> new Message.UserMessage(record.content());
            case "assistant" -> new Message.AssistantMessage(record.content());
            case "assistant_tool_use" -> new Message.AssistantToolUseMessage(record.content(), record.toolName(), record.toolUseId(), record.sourceAssistantMessageId());
            case "system" -> new Message.SystemMessage(record.content());
            case "tool_result" -> new Message.ToolResultMessage(record.content(), record.toolName(), record.toolUseId(), record.sourceAssistantMessageId());
            default -> throw new IllegalArgumentException("Unsupported message type: " + record.messageType());
        };
    }

    private SessionRuntime.CompletedTurnActivity materializeCompletedTurnActivity(SessionRecord record) {
        if (record.assistantMessageId() == null || record.assistantMessageId().isBlank()) {
            return null;
        }
        try {
            var assistantMessageId = UUID.fromString(record.assistantMessageId());
            var transcript = record.activityTimelineJson() == null || record.activityTimelineJson().isBlank()
                ? List.<SessionRuntime.CompletedTurnActivity.ActivityItem>of()
                : objectMapper.readValue(
                    record.activityTimelineJson(),
                    new TypeReference<List<SessionRuntime.CompletedTurnActivity.ActivityItem>>() {}
                );
            var fileSummary = record.fileSummaryJson() == null || record.fileSummaryJson().isBlank()
                ? null
                : objectMapper.readValue(record.fileSummaryJson(), SessionRuntime.CompletedTurnActivity.FileChangeSummary.class);
            if (transcript.isEmpty() && fileSummary == null) {
                return null;
            }
            return new SessionRuntime.CompletedTurnActivity(assistantMessageId, transcript, fileSummary);
        } catch (Exception exception) {
            return null;
        }
    }

    public Path transcriptPath(String cwd, UUID sessionId) {
        return projectDirectory(cwd).resolve(sessionId + ".jsonl");
    }

    private Path projectDirectory(String cwd) {
        return sessionsRoot.resolve(sanitize(cwd));
    }

    private static String sanitize(String cwd) {
        return cwd.replace(':', '_').replace('\\', '_').replace('/', '_').toLowerCase(Locale.ROOT);
    }

    private static Path defaultSessionsRoot() {
        var home = Path.of(System.getProperty("user.home"));
        return home.resolve(".coderhino").resolve("projects");
    }

    private static ObjectMapper defaultObjectMapper() {
        return new ObjectMapper().registerModule(new JavaTimeModule());
    }
}
