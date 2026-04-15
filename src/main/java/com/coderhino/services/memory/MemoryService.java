package com.coderhino.services.memory;

import com.coderhino.types.Message;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Persistent memory service for extracting and recalling notable facts from conversations.
 * Facts are stored in JSON files under the memory directory (default: ~/.claude/memory/).
 * Each session gets its own file: {memoryDir}/{sessionId}.json
 */
public final class MemoryService {

    private static final Pattern MEMORY_MARKER = Pattern.compile("\\[MEMORY:([^\\]]+)\\]");
    private static final int MAX_SUMMARY_LENGTH = 200;

    private final Path memoryDir;
    private final ObjectMapper objectMapper;

    /**
     * Creates a MemoryService using the default ~/.claude/memory/ directory.
     */
    public MemoryService() {
        this(defaultMemoryDir(), new ObjectMapper());
    }

    /**
     * Creates a MemoryService with a custom memory directory (for testing with @TempDir).
     *
     * @param memoryDir  the directory where session memory files will be stored
     * @param objectMapper the JSON mapper for reading/writing memory files
     */
    public MemoryService(Path memoryDir, ObjectMapper objectMapper) {
        this.memoryDir = memoryDir;
        this.objectMapper = objectMapper;
    }

    /**
     * Creates a MemoryService with a custom memory directory using the default ObjectMapper.
     *
     * @param memoryDir the directory where session memory files will be stored
     */
    public MemoryService(Path memoryDir) {
        this(memoryDir, new ObjectMapper());
    }

    /**
     * Extracts notable facts from conversation messages and persists them.
     * Scans messages for [MEMORY:...] markers or extracts key content from the last
     * user/assistant exchange. Writes facts to {memoryDir}/{sessionId}.json.
     *
     * @param sessionId the session identifier (used as the filename)
     * @param messages  the conversation messages to scan
     * @return the list of extracted facts written to the file
     */
    public List<String> extract(String sessionId, List<Message> messages) {
        if (sessionId == null || sessionId.isBlank()) {
            throw new IllegalArgumentException("sessionId must not be blank");
        }
        if (messages == null || messages.isEmpty()) {
            return List.of();
        }

        List<String> facts = new ArrayList<>();

        // First pass: scan for explicit [MEMORY:...] markers
        for (Message message : messages) {
            String content = message.content();
            if (content == null || content.isBlank()) {
                continue;
            }
            Matcher matcher = MEMORY_MARKER.matcher(content);
            while (matcher.find()) {
                String fact = matcher.group(1).trim();
                if (!fact.isBlank() && !facts.contains(fact)) {
                    facts.add(fact);
                }
            }
        }

        // Second pass: if no explicit markers found, extract from last user/assistant exchange
        if (facts.isEmpty()) {
            facts.addAll(extractLastExchangeSummary(messages));
        }

        if (facts.isEmpty()) {
            return List.of();
        }

        persistFacts(sessionId, facts);
        return Collections.unmodifiableList(facts);
    }

    /**
     * Recalls previously extracted facts for a given session.
     *
     * @param sessionId the session identifier
     * @return list of stored facts, or empty list if no facts exist for this session
     */
    public List<String> recall(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            return List.of();
        }

        Path memoryFile = memoryFilePath(sessionId);
        if (!Files.exists(memoryFile)) {
            return List.of();
        }

        try {
            String json = Files.readString(memoryFile, StandardCharsets.UTF_8);
            if (json.isBlank()) {
                return List.of();
            }
            List<String> facts = objectMapper.readValue(json, new TypeReference<List<String>>() {});
            return facts == null ? List.of() : Collections.unmodifiableList(facts);
        } catch (IOException exception) {
            // If file is malformed/corrupt, return empty list gracefully
            return List.of();
        }
    }

    /**
     * Appends additional facts to an existing session's memory file.
     *
     * @param sessionId  the session identifier
     * @param newFacts   the facts to append
     * @return the combined list of all facts (existing + new)
     */
    public List<String> append(String sessionId, List<String> newFacts) {
        if (sessionId == null || sessionId.isBlank()) {
            throw new IllegalArgumentException("sessionId must not be blank");
        }
        if (newFacts == null || newFacts.isEmpty()) {
            return recall(sessionId);
        }

        List<String> existing = new ArrayList<>(recall(sessionId));
        for (String fact : newFacts) {
            if (fact != null && !fact.isBlank() && !existing.contains(fact)) {
                existing.add(fact);
            }
        }

        persistFacts(sessionId, existing);
        return Collections.unmodifiableList(existing);
    }

    /**
     * Clears all memory for a given session.
     *
     * @param sessionId the session identifier
     */
    public void clear(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            return;
        }
        try {
            Files.deleteIfExists(memoryFilePath(sessionId));
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to clear memory for session %s: %s"
                .formatted(sessionId, exception.getMessage()), exception);
        }
    }

    /**
     * Returns the path to the memory file for a given session.
     */
    public Path memoryFilePath(String sessionId) {
        return memoryDir.resolve(sessionId + ".json");
    }

    /**
     * Returns the memory directory path.
     */
    public Path memoryDir() {
        return memoryDir;
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private List<String> extractLastExchangeSummary(List<Message> messages) {
        List<String> result = new ArrayList<>();

        // Find the last user message and last assistant message
        String lastUserContent = null;
        String lastAssistantContent = null;

        for (Message message : messages) {
            if (message instanceof Message.UserMessage) {
                lastUserContent = message.content();
            } else if (message instanceof Message.AssistantMessage) {
                lastAssistantContent = message.content();
            }
        }

        if (lastUserContent != null && !lastUserContent.isBlank()) {
            String summary = "User asked: " + summarize(lastUserContent);
            result.add(summary);
        }

        if (lastAssistantContent != null && !lastAssistantContent.isBlank()) {
            String summary = "Assistant said: " + summarize(lastAssistantContent);
            result.add(summary);
        }

        return result;
    }

    private String summarize(String content) {
        if (content == null) {
            return "";
        }
        String trimmed = content.trim();
        if (trimmed.length() <= MAX_SUMMARY_LENGTH) {
            return trimmed;
        }
        return trimmed.substring(0, MAX_SUMMARY_LENGTH - 3) + "...";
    }

    private void persistFacts(String sessionId, List<String> facts) {
        Path memoryFile = memoryFilePath(sessionId);
        try {
            Files.createDirectories(memoryDir);
            String json = objectMapper.writeValueAsString(facts);
            Files.writeString(
                memoryFile,
                json,
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING
            );
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to persist memory for session %s: %s"
                .formatted(sessionId, exception.getMessage()), exception);
        }
    }

    private static Path defaultMemoryDir() {
        return Path.of(System.getProperty("user.home"), ".claude", "memory");
    }
}
