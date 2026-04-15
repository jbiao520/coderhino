package com.coderhino.web.project;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.ArrayList;
import java.util.List;

public class ProjectWorkspaceState {

    @JsonProperty("openProjectIds")
    private List<String> openProjectIds;

    @JsonProperty("activeProjectId")
    private String activeProjectId;

    public ProjectWorkspaceState() {
        this(List.of(), null);
    }

    public ProjectWorkspaceState(List<String> openProjectIds, String activeProjectId) {
        this.openProjectIds = openProjectIds != null ? new ArrayList<>(openProjectIds) : new ArrayList<>();
        this.activeProjectId = activeProjectId;
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
