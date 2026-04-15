package com.coderhino.web.dto;

import com.coderhino.web.project.ProjectWorkspaceState;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.ArrayList;
import java.util.List;

public class ProjectWorkspaceStateDto {

    @JsonProperty("openProjectIds")
    private List<String> openProjectIds;

    @JsonProperty("activeProjectId")
    private String activeProjectId;

    public ProjectWorkspaceStateDto() {
        this.openProjectIds = new ArrayList<>();
    }

    public ProjectWorkspaceStateDto(List<String> openProjectIds, String activeProjectId) {
        this.openProjectIds = openProjectIds != null ? new ArrayList<>(openProjectIds) : new ArrayList<>();
        this.activeProjectId = activeProjectId;
    }

    public static ProjectWorkspaceStateDto from(ProjectWorkspaceState state) {
        return new ProjectWorkspaceStateDto(state.getOpenProjectIds(), state.getActiveProjectId());
    }

    public ProjectWorkspaceState toModel() {
        return new ProjectWorkspaceState(openProjectIds, activeProjectId);
    }

    public List<String> getOpenProjectIds() {
        return openProjectIds;
    }

    public void setOpenProjectIds(List<String> openProjectIds) {
        this.openProjectIds = openProjectIds != null ? new ArrayList<>(openProjectIds) : new ArrayList<>();
    }

    public String getActiveProjectId() {
        return activeProjectId;
    }

    public void setActiveProjectId(String activeProjectId) {
        this.activeProjectId = activeProjectId;
    }
}
