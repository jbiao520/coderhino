package com.coderhino.web.dto;

public class ProjectRenameRequest {

    private String name;

    public ProjectRenameRequest() {
    }

    public ProjectRenameRequest(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}
