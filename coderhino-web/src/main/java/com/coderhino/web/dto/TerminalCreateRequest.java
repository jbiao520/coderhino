package com.coderhino.web.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public class TerminalCreateRequest {

    @JsonProperty("label")
    private String label;

    @JsonProperty("worktreeId")
    private String worktreeId;

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public String getWorktreeId() {
        return worktreeId;
    }

    public void setWorktreeId(String worktreeId) {
        this.worktreeId = worktreeId;
    }
}
