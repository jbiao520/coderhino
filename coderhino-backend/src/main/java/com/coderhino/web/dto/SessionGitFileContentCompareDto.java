package com.coderhino.web.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class SessionGitFileContentCompareDto {

    @JsonProperty("path")
    private String path;

    @JsonProperty("previousContent")
    private String previousContent;

    @JsonProperty("currentContent")
    private String currentContent;

    public SessionGitFileContentCompareDto() {
    }

    public SessionGitFileContentCompareDto(String path, String previousContent, String currentContent) {
        this.path = path;
        this.previousContent = previousContent;
        this.currentContent = currentContent;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public String getPreviousContent() {
        return previousContent;
    }

    public void setPreviousContent(String previousContent) {
        this.previousContent = previousContent;
    }

    public String getCurrentContent() {
        return currentContent;
    }

    public void setCurrentContent(String currentContent) {
        this.currentContent = currentContent;
    }
}
