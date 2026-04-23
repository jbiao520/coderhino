package com.coderhino.web.session;

import com.coderhino.types.PermissionMode;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;

public class SessionMetadata {

    @JsonProperty("sessionId")
    private String sessionId;

    @JsonProperty("createdAt")
    private Instant createdAt;

    @JsonProperty("model")
    private String model;

    @JsonProperty("permissionMode")
    private String permissionMode;

    @JsonProperty("cwd")
    private String cwd;

    @JsonProperty("projectId")
    private String projectId;

    @JsonProperty("worktreeId")
    private String worktreeId;

    @JsonProperty("name")
    private String name;

    @JsonProperty("branch")
    private String branch;

    @JsonProperty("providerId")
    private String providerId;

    @JsonProperty("modelMode")
    private String modelMode;

    @JsonProperty("normalPermissionMode")
    private String normalPermissionMode;

    @JsonProperty("totalInputTokens")
    private Long totalInputTokens;

    @JsonProperty("totalOutputTokens")
    private Long totalOutputTokens;

    @JsonProperty("totalCacheReadTokens")
    private Long totalCacheReadTokens;

    @JsonProperty("totalCacheWriteTokens")
    private Long totalCacheWriteTokens;

    @JsonProperty("totalToolUses")
    private Integer totalToolUses;

    public SessionMetadata() {
    }

    public SessionMetadata(String sessionId, Instant createdAt, String model, String permissionMode, String cwd) {
        this(sessionId, createdAt, model, permissionMode, cwd, null);
    }

    public SessionMetadata(String sessionId, Instant createdAt, String model, String permissionMode, String cwd, String projectId) {
        this(sessionId, createdAt, model, permissionMode, cwd, projectId, null, null, null);
    }

    public SessionMetadata(String sessionId, Instant createdAt, String model, String permissionMode, String cwd, String projectId, String name) {
        this(sessionId, createdAt, model, permissionMode, cwd, projectId, null, name, null);
    }

    public SessionMetadata(String sessionId, Instant createdAt, String model, String permissionMode, String cwd, String projectId, String name, String branch) {
        this(sessionId, createdAt, model, permissionMode, cwd, projectId, null, name, branch);
    }

    public SessionMetadata(String sessionId, Instant createdAt, String model, String permissionMode, String cwd, String projectId, String worktreeId, String name, String branch) {
        this(sessionId, createdAt, model, permissionMode, cwd, projectId, worktreeId, name, branch, null, "default", PermissionMode.BYPASS.name(), null, null, null, null, null);
    }

    public SessionMetadata(String sessionId, Instant createdAt, String model, String permissionMode, String cwd,
                           String projectId, String worktreeId, String name, String branch,
                           String providerId,
                           String modelMode, String normalPermissionMode) {
        this(sessionId, createdAt, model, permissionMode, cwd, projectId, worktreeId, name, branch,
            providerId, modelMode, normalPermissionMode, null, null, null, null, null);
    }

    public SessionMetadata(String sessionId, Instant createdAt, String model, String permissionMode, String cwd,
                           String projectId, String worktreeId, String name, String branch,
                           String providerId,
                           String modelMode, String normalPermissionMode,
                           Long totalInputTokens, Long totalOutputTokens, Long totalCacheReadTokens,
                           Long totalCacheWriteTokens, Integer totalToolUses) {
        this.sessionId = sessionId;
        this.createdAt = createdAt;
        this.model = model;
        this.permissionMode = permissionMode;
        this.cwd = cwd;
        this.projectId = projectId;
        this.worktreeId = worktreeId;
        this.name = name;
        this.branch = branch;
        this.providerId = providerId;
        this.modelMode = modelMode;
        this.normalPermissionMode = normalPermissionMode;
        this.totalInputTokens = totalInputTokens;
        this.totalOutputTokens = totalOutputTokens;
        this.totalCacheReadTokens = totalCacheReadTokens;
        this.totalCacheWriteTokens = totalCacheWriteTokens;
        this.totalToolUses = totalToolUses;
    }

    public String getSessionId() { return sessionId; }
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }

    public String getPermissionMode() { return permissionMode; }
    public void setPermissionMode(String permissionMode) { this.permissionMode = permissionMode; }

    public String getCwd() { return cwd; }
    public void setCwd(String cwd) { this.cwd = cwd; }

    public String getProjectId() { return projectId; }
    public void setProjectId(String projectId) { this.projectId = projectId; }

    public String getWorktreeId() { return worktreeId; }
    public void setWorktreeId(String worktreeId) { this.worktreeId = worktreeId; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getBranch() { return branch; }
    public void setBranch(String branch) { this.branch = branch; }

    public String getProviderId() { return providerId; }
    public void setProviderId(String providerId) { this.providerId = providerId; }

    public String getModelMode() { return modelMode; }
    public void setModelMode(String modelMode) { this.modelMode = modelMode; }

    public String getNormalPermissionMode() { return normalPermissionMode; }
    public void setNormalPermissionMode(String normalPermissionMode) { this.normalPermissionMode = normalPermissionMode; }

    public Long getTotalInputTokens() { return totalInputTokens; }
    public void setTotalInputTokens(Long totalInputTokens) { this.totalInputTokens = totalInputTokens; }

    public Long getTotalOutputTokens() { return totalOutputTokens; }
    public void setTotalOutputTokens(Long totalOutputTokens) { this.totalOutputTokens = totalOutputTokens; }

    public Long getTotalCacheReadTokens() { return totalCacheReadTokens; }
    public void setTotalCacheReadTokens(Long totalCacheReadTokens) { this.totalCacheReadTokens = totalCacheReadTokens; }

    public Long getTotalCacheWriteTokens() { return totalCacheWriteTokens; }
    public void setTotalCacheWriteTokens(Long totalCacheWriteTokens) { this.totalCacheWriteTokens = totalCacheWriteTokens; }

    public Integer getTotalToolUses() { return totalToolUses; }
    public void setTotalToolUses(Integer totalToolUses) { this.totalToolUses = totalToolUses; }
}
