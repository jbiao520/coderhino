package com.coderhino.web.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.ArrayList;
import java.util.List;

public class ProjectListDto {

    @JsonProperty("projects")
    private List<ProjectDto> projects;

    @JsonProperty("count")
    private int count;

    public ProjectListDto() {
        this.projects = new ArrayList<>();
    }

    public ProjectListDto(List<ProjectDto> projects) {
        this.projects = new ArrayList<>(projects);
        this.count = projects.size();
    }

    public List<ProjectDto> getProjects() { return projects; }
    public void setProjects(List<ProjectDto> projects) {
        this.projects = projects;
        this.count = projects != null ? projects.size() : 0;
    }

    public int getCount() { return count; }
    public void setCount(int count) { this.count = count; }
}
