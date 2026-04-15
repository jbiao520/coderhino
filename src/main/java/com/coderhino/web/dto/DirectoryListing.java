package com.coderhino.web.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public class DirectoryListing {

    @JsonProperty("path")
    private String path;

    @JsonProperty("children")
    private List<FileNode> children;

    public DirectoryListing() {
    }

    public DirectoryListing(String path, List<FileNode> children) {
        this.path = path;
        this.children = children;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public List<FileNode> getChildren() {
        return children;
    }

    public void setChildren(List<FileNode> children) {
        this.children = children;
    }
}
