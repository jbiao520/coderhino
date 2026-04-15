package com.coderhino.web.dto;

import com.coderhino.web.session.WebSession;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class SessionContextDto {

    @JsonProperty("summary")
    private Summary summary;

    @JsonProperty("rawAiHistory")
    private List<RawAiHistoryEntry> rawAiHistory;

    public SessionContextDto() {
    }

    public SessionContextDto(Summary summary, List<RawAiHistoryEntry> rawAiHistory) {
        this.summary = summary;
        this.rawAiHistory = rawAiHistory != null ? List.copyOf(rawAiHistory) : List.of();
    }

    public static SessionContextDto from(WebSession session) {
        var state = session.getAppState();
        var run = session.getActiveRunId() != null && session.getCurrentRunStatus() != null
            ? new RunDto(session.getActiveRunId(), session.getCurrentRunStatus())
            : null;
        var replay = session.getActiveRunReplaySnapshot();
        var currentUsage = replay != null && replay.usage() != null
            ? new UsageSummary(
                Long.valueOf(replay.usage().inputTokens()),
                Long.valueOf(replay.usage().outputTokens()),
                Long.valueOf(replay.usage().cacheReadTokens()),
                Long.valueOf(replay.usage().cacheCreationTokens()),
                Integer.valueOf((int) replay.usage().toolUses()),
                Long.valueOf(replay.usage().contextLength())
            )
            : run != null && state.currentUsage() != null
                ? new UsageSummary(
                    Long.valueOf(state.currentUsage().inputTokens()),
                    Long.valueOf(state.currentUsage().outputTokens()),
                    Long.valueOf(state.currentUsage().cacheReadTokens()),
                    Long.valueOf(state.currentUsage().cacheWriteTokens()),
                    Integer.valueOf(state.currentUsage().toolUses()),
                    Long.valueOf(state.currentUsage().contextLength())
                )
                : null;
        var totalsSummary = new UsageSummary(
            state.totalInputTokens(),
            state.totalOutputTokens(),
            state.totalCacheReadTokens(),
            state.totalCacheWriteTokens(),
            state.totalToolUses(),
            state.totalInputTokens() + state.totalOutputTokens() + state.totalCacheReadTokens() + state.totalCacheWriteTokens()
        );

        var summary = new Summary(
            session.getSessionId(),
            session.getName(),
            state.model(),
            session.getProviderId(),
            state.permissionMode().name(),
            run != null ? run.getStatus().name() : "IDLE",
            session.getCreatedAt(),
            state.messages().size(),
            currentUsage,
            totalsSummary,
            run
        );

        var rawAiHistory = new ArrayList<RawAiHistoryEntry>();
        for (var entry : state.sessionRuntime().rawAiHistory()) {
            rawAiHistory.add(new RawAiHistoryEntry(
                entry.direction(),
                entry.timestamp(),
                entry.content()
            ));
        }

        return new SessionContextDto(summary, rawAiHistory);
    }
    public Summary getSummary() {
        return summary;
    }

    public void setSummary(Summary summary) {
        this.summary = summary;
    }

    public List<RawAiHistoryEntry> getRawAiHistory() {
        return rawAiHistory;
    }

    public void setRawAiHistory(List<RawAiHistoryEntry> rawAiHistory) {
        this.rawAiHistory = rawAiHistory != null ? List.copyOf(rawAiHistory) : List.of();
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Summary {
        @JsonProperty("sessionId")
        private String sessionId;

        @JsonProperty("name")
        private String name;

        @JsonProperty("model")
        private String model;

        @JsonProperty("providerId")
        private String providerId;

        @JsonProperty("permissionMode")
        private String permissionMode;

        @JsonProperty("status")
        private String status;

        @JsonProperty("createdAt")
        private Instant createdAt;

        @JsonProperty("messageCount")
        private int messageCount;

        @JsonProperty("currentUsage")
        private UsageSummary currentUsage;

        @JsonProperty("sessionTotals")
        private UsageSummary sessionTotals;

        @JsonProperty("activeRun")
        private RunDto activeRun;

        public Summary() {
        }

        public Summary(String sessionId, String name, String model, String providerId, String permissionMode,
                       String status, Instant createdAt, int messageCount,
                       UsageSummary currentUsage, UsageSummary sessionTotals, RunDto activeRun) {
            this.sessionId = sessionId;
            this.name = name;
            this.model = model;
            this.providerId = providerId;
            this.permissionMode = permissionMode;
            this.status = status;
            this.createdAt = createdAt;
            this.messageCount = messageCount;
            this.currentUsage = currentUsage;
            this.sessionTotals = sessionTotals;
            this.activeRun = activeRun;
        }

        public String getSessionId() { return sessionId; }
        public void setSessionId(String sessionId) { this.sessionId = sessionId; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getModel() { return model; }
        public void setModel(String model) { this.model = model; }
        public String getProviderId() { return providerId; }
        public void setProviderId(String providerId) { this.providerId = providerId; }
        public String getPermissionMode() { return permissionMode; }
        public void setPermissionMode(String permissionMode) { this.permissionMode = permissionMode; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        public Instant getCreatedAt() { return createdAt; }
        public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
        public int getMessageCount() { return messageCount; }
        public void setMessageCount(int messageCount) { this.messageCount = messageCount; }
        public UsageSummary getCurrentUsage() { return currentUsage; }
        public void setCurrentUsage(UsageSummary currentUsage) { this.currentUsage = currentUsage; }
        public UsageSummary getSessionTotals() { return sessionTotals; }
        public void setSessionTotals(UsageSummary sessionTotals) { this.sessionTotals = sessionTotals; }
        public RunDto getActiveRun() { return activeRun; }
        public void setActiveRun(RunDto activeRun) { this.activeRun = activeRun; }

        public Long getInputTokens() {
            return currentUsage != null ? currentUsage.getInputTokens() : null;
        }

        public Long getOutputTokens() {
            return currentUsage != null ? currentUsage.getOutputTokens() : null;
        }

        public Long getCacheReadTokens() {
            return currentUsage != null ? currentUsage.getCacheReadTokens() : null;
        }

        public Long getCacheWriteTokens() {
            return currentUsage != null ? currentUsage.getCacheWriteTokens() : null;
        }

        public Integer getToolUses() {
            return currentUsage != null ? currentUsage.getToolUses() : null;
        }

        public Long getContextLength() {
            return currentUsage != null ? currentUsage.getContextLength() : null;
        }
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class UsageSummary {
        @JsonProperty("inputTokens")
        private Long inputTokens;

        @JsonProperty("outputTokens")
        private Long outputTokens;

        @JsonProperty("cacheReadTokens")
        private Long cacheReadTokens;

        @JsonProperty("cacheWriteTokens")
        private Long cacheWriteTokens;

        @JsonProperty("toolUses")
        private Integer toolUses;

        @JsonProperty("contextLength")
        private Long contextLength;

        public UsageSummary() {
        }

        public UsageSummary(Long inputTokens, Long outputTokens, Long cacheReadTokens, Long cacheWriteTokens,
                            Integer toolUses, Long contextLength) {
            this.inputTokens = inputTokens;
            this.outputTokens = outputTokens;
            this.cacheReadTokens = cacheReadTokens;
            this.cacheWriteTokens = cacheWriteTokens;
            this.toolUses = toolUses;
            this.contextLength = contextLength;
        }

        public Long getInputTokens() { return inputTokens; }
        public void setInputTokens(Long inputTokens) { this.inputTokens = inputTokens; }
        public Long getOutputTokens() { return outputTokens; }
        public void setOutputTokens(Long outputTokens) { this.outputTokens = outputTokens; }
        public Long getCacheReadTokens() { return cacheReadTokens; }
        public void setCacheReadTokens(Long cacheReadTokens) { this.cacheReadTokens = cacheReadTokens; }
        public Long getCacheWriteTokens() { return cacheWriteTokens; }
        public void setCacheWriteTokens(Long cacheWriteTokens) { this.cacheWriteTokens = cacheWriteTokens; }
        public Integer getToolUses() { return toolUses; }
        public void setToolUses(Integer toolUses) { this.toolUses = toolUses; }
        public Long getContextLength() { return contextLength; }
        public void setContextLength(Long contextLength) { this.contextLength = contextLength; }
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class RawAiHistoryEntry {
        @JsonProperty("direction")
        private String direction;

        @JsonProperty("timestamp")
        private Instant timestamp;

        @JsonProperty("content")
        private String content;

        public RawAiHistoryEntry() {
        }

        public RawAiHistoryEntry(String direction, Instant timestamp, String content) {
            this.direction = direction;
            this.timestamp = timestamp;
            this.content = content;
        }

        public String getDirection() { return direction; }
        public void setDirection(String direction) { this.direction = direction; }
        public Instant getTimestamp() { return timestamp; }
        public void setTimestamp(Instant timestamp) { this.timestamp = timestamp; }
        public String getContent() { return content; }
        public void setContent(String content) { this.content = content; }
    }
}
