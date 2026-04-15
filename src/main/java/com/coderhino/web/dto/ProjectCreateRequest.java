package com.coderhino.web.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public class ProjectCreateRequest {

    @JsonProperty("path")
    private String path;

    public ProjectCreateRequest() {
    }

    public ProjectCreateRequest(String path) {
        this.path = path;
    }

    public String getPath() { return path; }
    public void setPath(String path) { this.path = path; }
}
