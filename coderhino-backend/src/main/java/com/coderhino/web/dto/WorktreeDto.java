package com.coderhino.web.dto;

import com.coderhino.web.git.GitBranchResolver;
import com.coderhino.web.project.Worktree;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.nio.file.Path;
import java.time.Instant;

public class WorktreeDto {

    @JsonProperty("id")
    private String id;

    @JsonProperty("name")
    private String name;

    @JsonProperty("path")
    private String path;

    @JsonProperty("defaultWorktree")
    private boolean defaultWorktree;

    @JsonProperty("managed")
    private boolean managed;

    @JsonProperty("createdAt")
    private Instant createdAt;

    @JsonProperty("branch")
    private String branch;

    public WorktreeDto() {
    }

    public WorktreeDto(String id, String name, String path, boolean defaultWorktree, boolean managed, Instant createdAt, String branch) {
        this.id = id;
        this.name = name;
        this.path = path;
        this.defaultWorktree = defaultWorktree;
        this.managed = managed;
        this.createdAt = createdAt;
        this.branch = branch;
    }

    public static WorktreeDto from(Worktree worktree) {
        String branch = null;
        if (worktree.isDefaultWorktree()) {
            branch = GitBranchResolver.resolve(Path.of(worktree.getPath()));
        }
        return new WorktreeDto(
            worktree.getId(),
            worktree.getName(),
            worktree.getPath(),
            worktree.isDefaultWorktree(),
            worktree.isManaged(),
            worktree.getCreatedAt(),
            branch
        );
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getPath() { return path; }
    public void setPath(String path) { this.path = path; }

    public boolean isDefaultWorktree() { return defaultWorktree; }
    public void setDefaultWorktree(boolean defaultWorktree) { this.defaultWorktree = defaultWorktree; }

    public boolean isManaged() { return managed; }
    public void setManaged(boolean managed) { this.managed = managed; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public String getBranch() { return branch; }
    public void setBranch(String branch) { this.branch = branch; }
}
