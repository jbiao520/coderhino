package com.coderhino.web.dto;

import com.coderhino.web.credentials.ApiCredentials;
import com.coderhino.web.session.WebSession;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public class SessionDto {

    @JsonProperty("sessionId")
    private String sessionId;

    @JsonProperty("createdAt")
    private Instant createdAt;

    @JsonProperty("model")
    private String model;

    @JsonProperty("permissionMode")
    private String permissionMode;

    @JsonProperty("messageCount")
    private int messageCount;

    @JsonProperty("activeRun")
    @JsonInclude(JsonInclude.Include.ALWAYS)
    private RunDto activeRun;

    @JsonProperty("activeRunState")
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private ActiveRunStateDto activeRunState;

    @JsonProperty("messages")
    private List<MessageDto> messages;

    @JsonProperty("projectId")
    private String projectId;

    @JsonProperty("name")
    private String name;

    @JsonProperty("branch")
    private String branch;

    @JsonProperty("providerId")
    private String providerId;

    @JsonProperty("availableProviders")
    private List<ProviderOptionDto> availableProviders;

    @JsonProperty("worktreeId")
    private String worktreeId;

    @JsonProperty("worktree")
    private WorktreeDto worktree;

    @JsonProperty("planMode")
    private boolean planMode;

    @JsonProperty("buildMode")
    private boolean buildMode;

    @JsonProperty("availableModels")
    private List<String> availableModels;

    @JsonProperty("modelMode")
    private String modelMode;

    @JsonProperty("modelModeSupported")
    private boolean modelModeSupported;

    @JsonProperty("availableModelModes")
    private List<String> availableModelModes;

    public SessionDto() {
    }

    public SessionDto(String sessionId, Instant createdAt, String model, String permissionMode,
                      int messageCount, RunDto activeRun, List<MessageDto> messages) {
        this(sessionId, createdAt, model, permissionMode, messageCount, activeRun, messages, null, null, null);
    }

    public SessionDto(String sessionId, Instant createdAt, String model, String permissionMode,
                      int messageCount, RunDto activeRun, List<MessageDto> messages, String projectId) {
        this(sessionId, createdAt, model, permissionMode, messageCount, activeRun, messages, projectId, null, null);
    }

    public SessionDto(String sessionId, Instant createdAt, String model, String permissionMode,
                      int messageCount, RunDto activeRun, List<MessageDto> messages, String projectId, String name) {
        this(sessionId, createdAt, model, permissionMode, messageCount, activeRun, messages, projectId, name, null);
    }

    public SessionDto(String sessionId, Instant createdAt, String model, String permissionMode,
                      int messageCount, RunDto activeRun, List<MessageDto> messages, String projectId, String name, String branch) {
        this(sessionId, createdAt, model, permissionMode, messageCount, activeRun, null, messages, projectId, name, branch, null, List.of(), null, null);
    }

    public SessionDto(String sessionId, Instant createdAt, String model, String permissionMode,
                      int messageCount, RunDto activeRun, List<MessageDto> messages, String projectId, String name, String branch,
                      String providerId, List<ProviderOptionDto> availableProviders, String worktreeId, WorktreeDto worktree) {
        this(sessionId, createdAt, model, permissionMode, messageCount, activeRun, null, messages, projectId, name, branch, providerId, availableProviders, worktreeId, worktree);
    }

    public SessionDto(String sessionId, Instant createdAt, String model, String permissionMode,
                      int messageCount, RunDto activeRun, ActiveRunStateDto activeRunState, List<MessageDto> messages, String projectId, String name, String branch,
                      String providerId, List<ProviderOptionDto> availableProviders, String worktreeId, WorktreeDto worktree) {
        this(sessionId, createdAt, model, permissionMode, messageCount, activeRun, activeRunState, messages, projectId, name, branch, providerId, availableProviders,
            worktreeId, worktree, false, true, List.of(), "default", false, List.of());
    }

    public SessionDto(String sessionId, Instant createdAt, String model, String permissionMode,
                      int messageCount, RunDto activeRun, ActiveRunStateDto activeRunState, List<MessageDto> messages, String projectId, String name, String branch,
                      String providerId, List<ProviderOptionDto> availableProviders, String worktreeId, WorktreeDto worktree,
                      boolean planMode, boolean buildMode, List<String> availableModels,
                      String modelMode, boolean modelModeSupported, List<String> availableModelModes) {
        this.sessionId = sessionId;
        this.createdAt = createdAt;
        this.model = model;
        this.permissionMode = permissionMode;
        this.messageCount = messageCount;
        this.activeRun = activeRun;
        this.activeRunState = activeRunState;
        this.messages = messages != null ? new ArrayList<>(messages) : new ArrayList<>();
        this.projectId = projectId;
        this.name = name;
        this.branch = branch;
        this.providerId = providerId;
        this.availableProviders = availableProviders != null ? List.copyOf(availableProviders) : List.of();
        this.worktreeId = worktreeId;
        this.worktree = worktree;
        this.planMode = planMode;
        this.buildMode = buildMode;
        this.availableModels = availableModels != null ? List.copyOf(availableModels) : List.of();
        this.modelMode = modelMode;
        this.modelModeSupported = modelModeSupported;
        this.availableModelModes = availableModelModes != null ? List.copyOf(availableModelModes) : List.of();
    }

    public static SessionDto from(WebSession session) {
        return from(session, null, null, null);
    }

    public static SessionDto from(WebSession session, String projectId) {
        return from(session, projectId, null, null);
    }

    public static SessionDto from(WebSession session, String projectId, com.coderhino.web.project.Worktree worktree) {
        return from(session, projectId, worktree, null);
    }

    public static SessionDto from(WebSession session, String projectId, com.coderhino.web.project.Worktree worktree, ApiCredentials credentials) {
        var appState = session.getAppState();
        var transcript = appState.sessionRuntime().transcript();
        var messageDtos = new ArrayList<MessageDto>();
        for (int i = 0; i < transcript.size(); i++) {
            var envelope = transcript.get(i);
            var message = envelope.message();
            var type = message.type();
            if (!"user".equals(type) && !"assistant".equals(type) && !"system".equals(type)) {
                continue;
            }
            var completedActivity = "assistant".equals(type)
                ? findCompletedActivity(appState.sessionRuntime(), envelope.uuid())
                : null;
            messageDtos.add(new MessageDto(
                type,
                message.content(),
                envelope.timestamp(),
                "user".equals(type) ? i : null,
                completedActivity == null ? null : completedActivity.transcript().stream()
                    .map(item -> new ActiveRunTranscriptItemDto(
                        item.kind(),
                        item.content(),
                        item.toolName(),
                        item.toolUseId(),
                        item.argumentsJson(),
                        item.output()
                    )).toList(),
                completedActivity == null || completedActivity.fileSummary() == null ? null : new ActiveRunFileSummaryDto(
                    completedActivity.fileSummary().totalChanges(),
                    completedActivity.fileSummary().created(),
                    completedActivity.fileSummary().modified(),
                    completedActivity.fileSummary().deleted()
                )
            ));
        }
        RunDto activeRun = buildActiveRun(session);
        ActiveRunStateDto activeRunState = buildActiveRunState(session);
        var model = appState.model();
        var providerId = resolveProviderId(session, credentials);
        var selectedProvider = selectProvider(credentials, providerId);
        var availableModels = buildAvailableModels(model, selectedProvider);
        var availableProviders = buildAvailableProviders(providerId, model, credentials);
        var modelModes = buildModelModes(model);
        var modelModeSupported = !modelModes.isEmpty();
        return new SessionDto(
            session.getSessionId(),
            session.getCreatedAt(),
            model,
            appState.permissionMode().name(),
            session.getMessageCount(),
            activeRun,
            activeRunState,
            messageDtos,
            projectId,
            session.getName(),
            session.getBranch(),
            providerId,
            availableProviders,
            worktree != null ? worktree.getId() : null,
            worktree != null ? WorktreeDto.from(worktree) : null,
            appState.permissionMode() == com.coderhino.types.PermissionMode.PLAN,
            appState.permissionMode() != com.coderhino.types.PermissionMode.PLAN,
            availableModels,
            modelModeSupported ? session.getModelMode() : null,
            modelModeSupported,
            modelModes
        );
    }

    public static SessionDto fromWithoutMessages(WebSession session) {
        return fromWithoutMessages(session, null, null, null);
    }

    public static SessionDto fromWithoutMessages(WebSession session, String projectId) {
        return fromWithoutMessages(session, projectId, null, null);
    }

    public static SessionDto fromWithoutMessages(WebSession session, String projectId, com.coderhino.web.project.Worktree worktree) {
        return fromWithoutMessages(session, projectId, worktree, null);
    }

    public static SessionDto fromWithoutMessages(WebSession session, String projectId, com.coderhino.web.project.Worktree worktree, ApiCredentials credentials) {
        var appState = session.getAppState();
        RunDto activeRun = buildActiveRun(session);
        ActiveRunStateDto activeRunState = buildActiveRunState(session);
        var model = appState.model();
        var providerId = resolveProviderId(session, credentials);
        var selectedProvider = selectProvider(credentials, providerId);
        var availableModels = buildAvailableModels(model, selectedProvider);
        var availableProviders = buildAvailableProviders(providerId, model, credentials);
        var modelModes = buildModelModes(model);
        var modelModeSupported = !modelModes.isEmpty();
        return new SessionDto(
            session.getSessionId(),
            session.getCreatedAt(),
            model,
            appState.permissionMode().name(),
            session.getMessageCount(),
            activeRun,
            activeRunState,
            new ArrayList<>(),
            projectId,
            session.getName(),
            session.getBranch(),
            providerId,
            availableProviders,
            worktree != null ? worktree.getId() : null,
            worktree != null ? WorktreeDto.from(worktree) : null,
            appState.permissionMode() == com.coderhino.types.PermissionMode.PLAN,
            appState.permissionMode() != com.coderhino.types.PermissionMode.PLAN,
            availableModels,
            modelModeSupported ? session.getModelMode() : null,
            modelModeSupported,
            modelModes
        );
    }

    private static String resolveProviderId(WebSession session, ApiCredentials credentials) {
        if (session.getProviderId() != null && !session.getProviderId().isBlank()) {
            return session.getProviderId();
        }
        var defaultProvider = credentials != null ? credentials.getDefaultProvider() : null;
        return defaultProvider != null ? defaultProvider.getId() : null;
    }

    private static ApiCredentials.ApiProvider selectProvider(ApiCredentials credentials, String providerId) {
        if (credentials == null) {
            return null;
        }
        if (providerId != null && !providerId.isBlank()) {
            return credentials.findProvider(providerId);
        }
        return credentials.getDefaultProvider();
    }

    private static List<String> buildAvailableModels(String currentModel, ApiCredentials.ApiProvider provider) {
        var models = new ArrayList<String>();
        if (provider != null && provider.getModels() != null) {
            models.addAll(provider.getModelIds());
        }
        if (currentModel == null || currentModel.isBlank()) {
            return models;
        }
        if (!models.contains(currentModel)) {
            models.add(0, currentModel);
        }
        return models;
    }

    private static List<ProviderOptionDto> buildAvailableProviders(String selectedProviderId, String currentModel, ApiCredentials credentials) {
        var providers = new ArrayList<ProviderOptionDto>();
        var matched = false;
        if (credentials != null && credentials.getProviders() != null) {
            for (var provider : credentials.getProviders()) {
                var selected = provider.getId() != null && provider.getId().equals(selectedProviderId);
                providers.add(new ProviderOptionDto(
                    provider.getId(),
                    provider.getName(),
                    buildAvailableModels(selected ? currentModel : null, provider),
                    buildModelOptions(selected ? currentModel : null, provider != null ? provider.getModelIds() : List.of()),
                    false
                ));
                if (provider.getId() != null && provider.getId().equals(selectedProviderId)) {
                    matched = true;
                }
            }
        }
        if (selectedProviderId != null && !selectedProviderId.isBlank() && !matched) {
            providers.add(0, new ProviderOptionDto(
                selectedProviderId,
                selectedProviderId + " (unavailable)",
                currentModel == null || currentModel.isBlank() ? List.of() : List.of(currentModel),
                buildModelOptions(currentModel, List.of()),
                true
            ));
        }
        return providers;
    }

    private static List<ModelOptionDto> buildModelOptions(String currentModel, List<String> providerModels) {
        return buildAvailableModels(currentModel, providerModels).stream()
            .map(model -> new ModelOptionDto(model, model, buildModelModes(model)))
            .toList();
    }

    private static List<String> buildAvailableModels(String currentModel, List<String> providerModels) {
        var models = new ArrayList<String>();
        if (providerModels != null) {
            models.addAll(providerModels);
        }
        if (currentModel == null || currentModel.isBlank()) {
            return models;
        }
        if (!models.contains(currentModel)) {
            models.add(0, currentModel);
        }
        return models;
    }

    private static List<String> buildModelModes(String model) {
        if (model == null || model.isBlank()) {
            return List.of();
        }
        var normalized = model.toLowerCase();
        if (normalized.contains("sonnet") || normalized.contains("opus")) {
            return List.of("default", "think");
        }
        if (normalized.contains("gpt-5") || normalized.contains("o3") || normalized.contains("o4")) {
            return List.of("default", "low", "high");
        }
        return List.of();
    }

    private static RunDto buildActiveRun(WebSession session) {
        var runId = session.getActiveRunId();
        var status = session.getCurrentRunStatus();
        if (runId != null && status != null) {
            return new RunDto(runId, status);
        }
        return null;
    }

    private static ActiveRunStateDto buildActiveRunState(WebSession session) {
        var replay = session.getActiveRunReplaySnapshot();
        if (replay == null) {
            return null;
        }
        return new ActiveRunStateDto(
            replay.runId(),
            replay.transcript().stream().map(item -> new ActiveRunTranscriptItemDto(
                item.kind(),
                item.content(),
                item.toolName(),
                item.toolUseId(),
                item.argumentsJson(),
                item.output()
            )).toList(),
            replay.usage() == null ? null : new ActiveRunUsageDto(
                replay.usage().inputTokens(),
                replay.usage().outputTokens(),
                replay.usage().cacheCreationTokens(),
                replay.usage().cacheReadTokens(),
                replay.usage().toolUses(),
                replay.usage().contextLength()
            ),
            replay.lastSequence(),
            replay.terminalStatus(),
            replay.finalText(),
            replay.error(),
            replay.fileSummary() == null ? null : new ActiveRunFileSummaryDto(
                replay.fileSummary().totalChanges(),
                replay.fileSummary().created(),
                replay.fileSummary().modified(),
                replay.fileSummary().deleted()
            ),
            replay.pendingQuestion() == null ? null : new PendingQuestionDto(
                replay.runId(),
                replay.pendingQuestion().toolUseId(),
                replay.pendingQuestion().question(),
                replay.pendingQuestion().choices()
            )
        );
    }

    private static com.coderhino.state.SessionRuntime.CompletedTurnActivity findCompletedActivity(com.coderhino.state.SessionRuntime runtime, java.util.UUID assistantMessageId) {
        return runtime.completedTurnActivities().stream()
            .filter(activity -> activity.assistantMessageId().equals(assistantMessageId))
            .findFirst()
            .orElse(null);
    }

    public String getSessionId() { return sessionId; }
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }

    public String getPermissionMode() { return permissionMode; }
    public void setPermissionMode(String permissionMode) { this.permissionMode = permissionMode; }

    public int getMessageCount() { return messageCount; }
    public void setMessageCount(int messageCount) { this.messageCount = messageCount; }

    public RunDto getActiveRun() { return activeRun; }
    public void setActiveRun(RunDto activeRun) { this.activeRun = activeRun; }

    public ActiveRunStateDto getActiveRunState() { return activeRunState; }
    public void setActiveRunState(ActiveRunStateDto activeRunState) { this.activeRunState = activeRunState; }

    public List<MessageDto> getMessages() { return messages; }
    public void setMessages(List<MessageDto> messages) { this.messages = messages; }

    public String getProjectId() { return projectId; }
    public void setProjectId(String projectId) { this.projectId = projectId; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getBranch() { return branch; }
    public void setBranch(String branch) { this.branch = branch; }

    public String getProviderId() { return providerId; }
    public void setProviderId(String providerId) { this.providerId = providerId; }

    public List<ProviderOptionDto> getAvailableProviders() { return availableProviders; }
    public void setAvailableProviders(List<ProviderOptionDto> availableProviders) { this.availableProviders = availableProviders; }

    public String getWorktreeId() { return worktreeId; }
    public void setWorktreeId(String worktreeId) { this.worktreeId = worktreeId; }

    public WorktreeDto getWorktree() { return worktree; }
    public void setWorktree(WorktreeDto worktree) { this.worktree = worktree; }

    public boolean isPlanMode() { return planMode; }
    public void setPlanMode(boolean planMode) { this.planMode = planMode; }

    public boolean isBuildMode() { return buildMode; }
    public void setBuildMode(boolean buildMode) { this.buildMode = buildMode; }

    public List<String> getAvailableModels() { return availableModels; }
    public void setAvailableModels(List<String> availableModels) { this.availableModels = availableModels; }

    public String getModelMode() { return modelMode; }
    public void setModelMode(String modelMode) { this.modelMode = modelMode; }

    public boolean isModelModeSupported() { return modelModeSupported; }
    public void setModelModeSupported(boolean modelModeSupported) { this.modelModeSupported = modelModeSupported; }

    public List<String> getAvailableModelModes() { return availableModelModes; }
    public void setAvailableModelModes(List<String> availableModelModes) { this.availableModelModes = availableModelModes; }

    public static class MessageDto {
        @JsonProperty("type")
        private String type;

        @JsonProperty("content")
        private String content;

        @JsonProperty("timestamp")
        private Instant timestamp;

        @JsonProperty("rollbackIndex")
        private Integer rollbackIndex;

        @JsonProperty("activityTimeline")
        @JsonInclude(JsonInclude.Include.NON_NULL)
        private List<ActiveRunTranscriptItemDto> activityTimeline;

        @JsonProperty("fileSummary")
        @JsonInclude(JsonInclude.Include.NON_NULL)
        private ActiveRunFileSummaryDto fileSummary;

        public MessageDto() {}

        public MessageDto(String type, String content) {
            this(type, content, null, null, null, null);
        }

        public MessageDto(String type, String content, Instant timestamp, Integer rollbackIndex) {
            this(type, content, timestamp, rollbackIndex, null, null);
        }

        public MessageDto(String type, String content, Instant timestamp, Integer rollbackIndex,
                          List<ActiveRunTranscriptItemDto> activityTimeline, ActiveRunFileSummaryDto fileSummary) {
            this.type = type;
            this.content = content;
            this.timestamp = timestamp;
            this.rollbackIndex = rollbackIndex;
            this.activityTimeline = activityTimeline != null ? List.copyOf(activityTimeline) : null;
            this.fileSummary = fileSummary;
        }

        public String getType() { return type; }
        public void setType(String type) { this.type = type; }

        public String getContent() { return content; }
        public void setContent(String content) { this.content = content; }

        public Instant getTimestamp() { return timestamp; }
        public void setTimestamp(Instant timestamp) { this.timestamp = timestamp; }

        public Integer getRollbackIndex() { return rollbackIndex; }
        public void setRollbackIndex(Integer rollbackIndex) { this.rollbackIndex = rollbackIndex; }

        public List<ActiveRunTranscriptItemDto> getActivityTimeline() { return activityTimeline; }
        public void setActivityTimeline(List<ActiveRunTranscriptItemDto> activityTimeline) { this.activityTimeline = activityTimeline; }

        public ActiveRunFileSummaryDto getFileSummary() { return fileSummary; }
        public void setFileSummary(ActiveRunFileSummaryDto fileSummary) { this.fileSummary = fileSummary; }
    }

    public static class ActiveRunStateDto {
        @JsonProperty("runId")
        private String runId;

        @JsonProperty("transcript")
        private List<ActiveRunTranscriptItemDto> transcript;

        @JsonProperty("usage")
        @JsonInclude(JsonInclude.Include.NON_NULL)
        private ActiveRunUsageDto usage;

        @JsonProperty("lastSequence")
        @JsonInclude(JsonInclude.Include.NON_NULL)
        private Long lastSequence;

        @JsonProperty("terminalStatus")
        @JsonInclude(JsonInclude.Include.NON_NULL)
        private String terminalStatus;

        @JsonProperty("finalText")
        @JsonInclude(JsonInclude.Include.NON_NULL)
        private String finalText;

        @JsonProperty("error")
        @JsonInclude(JsonInclude.Include.NON_NULL)
        private String error;

        @JsonProperty("fileSummary")
        @JsonInclude(JsonInclude.Include.NON_NULL)
        private ActiveRunFileSummaryDto fileSummary;

        @JsonProperty("pendingQuestion")
        @JsonInclude(JsonInclude.Include.NON_NULL)
        private PendingQuestionDto pendingQuestion;

        public ActiveRunStateDto() {
        }

        public ActiveRunStateDto(String runId, List<ActiveRunTranscriptItemDto> transcript, ActiveRunUsageDto usage,
                                 Long lastSequence, String terminalStatus, String finalText, String error,
                                 ActiveRunFileSummaryDto fileSummary, PendingQuestionDto pendingQuestion) {
            this.runId = runId;
            this.transcript = transcript != null ? List.copyOf(transcript) : List.of();
            this.usage = usage;
            this.lastSequence = lastSequence;
            this.terminalStatus = terminalStatus;
            this.finalText = finalText;
            this.error = error;
            this.fileSummary = fileSummary;
            this.pendingQuestion = pendingQuestion;
        }

        public String getRunId() { return runId; }
        public void setRunId(String runId) { this.runId = runId; }

        public List<ActiveRunTranscriptItemDto> getTranscript() { return transcript; }
        public void setTranscript(List<ActiveRunTranscriptItemDto> transcript) { this.transcript = transcript; }

        public ActiveRunUsageDto getUsage() { return usage; }
        public void setUsage(ActiveRunUsageDto usage) { this.usage = usage; }

        public Long getLastSequence() { return lastSequence; }
        public void setLastSequence(Long lastSequence) { this.lastSequence = lastSequence; }

        public String getTerminalStatus() { return terminalStatus; }
        public void setTerminalStatus(String terminalStatus) { this.terminalStatus = terminalStatus; }

        public String getFinalText() { return finalText; }
        public void setFinalText(String finalText) { this.finalText = finalText; }

        public String getError() { return error; }
        public void setError(String error) { this.error = error; }

        public ActiveRunFileSummaryDto getFileSummary() { return fileSummary; }
        public void setFileSummary(ActiveRunFileSummaryDto fileSummary) { this.fileSummary = fileSummary; }

        public PendingQuestionDto getPendingQuestion() { return pendingQuestion; }
        public void setPendingQuestion(PendingQuestionDto pendingQuestion) { this.pendingQuestion = pendingQuestion; }
    }

    public static class PendingQuestionDto {
        @JsonProperty("runId")
        @JsonInclude(JsonInclude.Include.NON_NULL)
        private String runId;

        @JsonProperty("toolUseId")
        private String toolUseId;

        @JsonProperty("question")
        private String question;

        @JsonProperty("choices")
        private List<String> choices;

        public PendingQuestionDto() {
        }

        public PendingQuestionDto(String runId, String toolUseId, String question, List<String> choices) {
            this.runId = runId;
            this.toolUseId = toolUseId;
            this.question = question;
            this.choices = choices != null ? List.copyOf(choices) : List.of();
        }

        public String getRunId() { return runId; }
        public void setRunId(String runId) { this.runId = runId; }

        public String getToolUseId() { return toolUseId; }
        public void setToolUseId(String toolUseId) { this.toolUseId = toolUseId; }

        public String getQuestion() { return question; }
        public void setQuestion(String question) { this.question = question; }

        public List<String> getChoices() { return choices; }
        public void setChoices(List<String> choices) { this.choices = choices; }
    }

    public static class ActiveRunTranscriptItemDto {
        @JsonProperty("kind")
        private String kind;

        @JsonProperty("content")
        @JsonInclude(JsonInclude.Include.NON_NULL)
        private String content;

        @JsonProperty("toolName")
        @JsonInclude(JsonInclude.Include.NON_NULL)
        private String toolName;

        @JsonProperty("toolUseId")
        @JsonInclude(JsonInclude.Include.NON_NULL)
        private String toolUseId;

        @JsonProperty("argumentsJson")
        @JsonInclude(JsonInclude.Include.NON_NULL)
        private String argumentsJson;

        @JsonProperty("output")
        @JsonInclude(JsonInclude.Include.NON_NULL)
        private String output;

        public ActiveRunTranscriptItemDto() {
        }

        public ActiveRunTranscriptItemDto(String kind, String content, String toolName, String toolUseId, String argumentsJson, String output) {
            this.kind = kind;
            this.content = content;
            this.toolName = toolName;
            this.toolUseId = toolUseId;
            this.argumentsJson = argumentsJson;
            this.output = output;
        }

        public String getKind() { return kind; }
        public void setKind(String kind) { this.kind = kind; }

        public String getContent() { return content; }
        public void setContent(String content) { this.content = content; }

        public String getToolName() { return toolName; }
        public void setToolName(String toolName) { this.toolName = toolName; }

        public String getToolUseId() { return toolUseId; }
        public void setToolUseId(String toolUseId) { this.toolUseId = toolUseId; }

        public String getArgumentsJson() { return argumentsJson; }
        public void setArgumentsJson(String argumentsJson) { this.argumentsJson = argumentsJson; }

        public String getOutput() { return output; }
        public void setOutput(String output) { this.output = output; }
    }

    public static class ActiveRunUsageDto {
        @JsonProperty("inputTokens")
        private long inputTokens;

        @JsonProperty("outputTokens")
        private long outputTokens;

        @JsonProperty("cacheCreationTokens")
        private long cacheCreationTokens;

        @JsonProperty("cacheReadTokens")
        private long cacheReadTokens;

        @JsonProperty("toolUses")
        private long toolUses;

        @JsonProperty("contextLength")
        private long contextLength;

        public ActiveRunUsageDto() {
        }

        public ActiveRunUsageDto(long inputTokens, long outputTokens, long cacheCreationTokens, long cacheReadTokens,
                                 long toolUses, long contextLength) {
            this.inputTokens = inputTokens;
            this.outputTokens = outputTokens;
            this.cacheCreationTokens = cacheCreationTokens;
            this.cacheReadTokens = cacheReadTokens;
            this.toolUses = toolUses;
            this.contextLength = contextLength;
        }

        public long getInputTokens() { return inputTokens; }
        public void setInputTokens(long inputTokens) { this.inputTokens = inputTokens; }

        public long getOutputTokens() { return outputTokens; }
        public void setOutputTokens(long outputTokens) { this.outputTokens = outputTokens; }

        public long getCacheCreationTokens() { return cacheCreationTokens; }
        public void setCacheCreationTokens(long cacheCreationTokens) { this.cacheCreationTokens = cacheCreationTokens; }

        public long getCacheReadTokens() { return cacheReadTokens; }
        public void setCacheReadTokens(long cacheReadTokens) { this.cacheReadTokens = cacheReadTokens; }

        public long getToolUses() { return toolUses; }
        public void setToolUses(long toolUses) { this.toolUses = toolUses; }

        public long getContextLength() { return contextLength; }
        public void setContextLength(long contextLength) { this.contextLength = contextLength; }
    }

    public static class ActiveRunFileSummaryDto {
        @JsonProperty("totalChanges")
        private int totalChanges;

        @JsonProperty("created")
        private List<String> created;

        @JsonProperty("modified")
        private List<String> modified;

        @JsonProperty("deleted")
        private List<String> deleted;

        public ActiveRunFileSummaryDto() {
        }

        public ActiveRunFileSummaryDto(int totalChanges, List<String> created, List<String> modified, List<String> deleted) {
            this.totalChanges = totalChanges;
            this.created = created != null ? List.copyOf(created) : List.of();
            this.modified = modified != null ? List.copyOf(modified) : List.of();
            this.deleted = deleted != null ? List.copyOf(deleted) : List.of();
        }

        public int getTotalChanges() { return totalChanges; }
        public void setTotalChanges(int totalChanges) { this.totalChanges = totalChanges; }

        public List<String> getCreated() { return created; }
        public void setCreated(List<String> created) { this.created = created; }

        public List<String> getModified() { return modified; }
        public void setModified(List<String> modified) { this.modified = modified; }

        public List<String> getDeleted() { return deleted; }
        public void setDeleted(List<String> deleted) { this.deleted = deleted; }
    }

    public static class ProviderOptionDto {
        @JsonProperty("id")
        private String id;

        @JsonProperty("name")
        private String name;

        @JsonProperty("models")
        private List<String> models;

        @JsonProperty("modelOptions")
        private List<ModelOptionDto> modelOptions;

        @JsonProperty("unavailable")
        private boolean unavailable;

        public ProviderOptionDto() {
        }

        public ProviderOptionDto(String id, String name, List<String> models, boolean unavailable) {
            this(id, name, models, List.of(), unavailable);
        }

        public ProviderOptionDto(String id, String name, List<String> models, List<ModelOptionDto> modelOptions, boolean unavailable) {
            this.id = id;
            this.name = name;
            this.models = models != null ? List.copyOf(models) : List.of();
            this.modelOptions = modelOptions != null ? List.copyOf(modelOptions) : List.of();
            this.unavailable = unavailable;
        }

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }

        public List<String> getModels() { return models; }
        public void setModels(List<String> models) { this.models = models; }

        public List<ModelOptionDto> getModelOptions() { return modelOptions; }
        public void setModelOptions(List<ModelOptionDto> modelOptions) { this.modelOptions = modelOptions; }

        public boolean isUnavailable() { return unavailable; }
        public void setUnavailable(boolean unavailable) { this.unavailable = unavailable; }
    }

    public static class ModelOptionDto {
        @JsonProperty("id")
        private String id;

        @JsonProperty("label")
        private String label;

        @JsonProperty("modelModeSupported")
        private boolean modelModeSupported;

        @JsonProperty("availableModelModes")
        private List<String> availableModelModes;

        public ModelOptionDto() {
        }

        public ModelOptionDto(String id, String label, List<String> availableModelModes) {
            this.id = id;
            this.label = label;
            this.availableModelModes = availableModelModes != null ? List.copyOf(availableModelModes) : List.of();
            this.modelModeSupported = !this.availableModelModes.isEmpty();
        }

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }

        public String getLabel() { return label; }
        public void setLabel(String label) { this.label = label; }

        public boolean isModelModeSupported() { return modelModeSupported; }
        public void setModelModeSupported(boolean modelModeSupported) { this.modelModeSupported = modelModeSupported; }

        public List<String> getAvailableModelModes() { return availableModelModes; }
        public void setAvailableModelModes(List<String> availableModelModes) {
            this.availableModelModes = availableModelModes != null ? List.copyOf(availableModelModes) : List.of();
            this.modelModeSupported = !this.availableModelModes.isEmpty();
        }
    }
}
