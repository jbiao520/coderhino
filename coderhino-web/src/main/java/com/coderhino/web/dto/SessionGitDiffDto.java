package com.coderhino.web.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class SessionGitDiffDto {

    @JsonProperty("kind")
    private String kind;

    @JsonProperty("path")
    private String path;

    @JsonProperty("diff")
    private String diff;

    public SessionGitDiffDto() {
    }

    public SessionGitDiffDto(String kind, String path, String diff) {
        this.kind = kind;
        this.path = path;
        this.diff = diff;
    }

    public String getKind() {
        return kind;
    }

    public void setKind(String kind) {
        this.kind = kind;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public String getDiff() {
        return diff;
    }

    public void setDiff(String diff) {
        this.diff = diff;
    }
}
