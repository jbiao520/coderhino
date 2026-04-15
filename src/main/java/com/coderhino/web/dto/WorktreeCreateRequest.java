package com.coderhino.web.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public class WorktreeCreateRequest {

    @JsonProperty("name")
    private String name;

    public WorktreeCreateRequest() {
    }

    public WorktreeCreateRequest(String name) {
        this.name = name;
    }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
}
