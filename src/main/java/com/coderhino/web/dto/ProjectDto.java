package com.coderhino.web.dto;

import com.coderhino.web.project.Project;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public class ProjectDto {

    @JsonProperty("id")
    private String id;

    @JsonProperty("name")
    private String name;

    @JsonProperty("path")
    private String path;

    @JsonProperty("lastOpened")
    private Instant lastOpened;

    @JsonProperty("createdAt")
    private Instant createdAt;

    @JsonProperty("workspaceEnabled")
    private boolean workspaceEnabled;

    @JsonProperty("worktrees")
    private List<WorktreeDto> worktrees;

    public ProjectDto() {
        this.worktrees = new ArrayList<>();
    }

    public ProjectDto(String id, String name, String path, Instant lastOpened, Instant createdAt) {
        this(id, name, path, lastOpened, createdAt, false, List.of());
    }

    public ProjectDto(String id, String name, String path, Instant lastOpened, Instant createdAt, boolean workspaceEnabled, List<WorktreeDto> worktrees) {
        this.id = id;
        this.name = name;
        this.path = path;
        this.lastOpened = lastOpened;
        this.createdAt = createdAt;
        this.workspaceEnabled = workspaceEnabled;
        this.worktrees = worktrees != null ? new ArrayList<>(worktrees) : new ArrayList<>();
    }

    public static ProjectDto from(Project project) {
        return new ProjectDto(
            project.getId(),
            project.getName(),
            project.getPath(),
            project.getLastOpened(),
            project.getCreatedAt(),
            project.isWorkspaceEnabled(),
            project.getWorktrees().stream().map(WorktreeDto::from).toList()
        );
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getPath() { return path; }
    public void setPath(String path) { this.path = path; }

    public Instant getLastOpened() { return lastOpened; }
    public void setLastOpened(Instant lastOpened) { this.lastOpened = lastOpened; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public boolean isWorkspaceEnabled() { return workspaceEnabled; }
    public void setWorkspaceEnabled(boolean workspaceEnabled) { this.workspaceEnabled = workspaceEnabled; }

    public List<WorktreeDto> getWorktrees() { return worktrees; }
    public void setWorktrees(List<WorktreeDto> worktrees) { this.worktrees = worktrees != null ? new ArrayList<>(worktrees) : new ArrayList<>(); }
}
