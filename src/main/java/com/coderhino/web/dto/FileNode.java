package com.coderhino.web.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class FileNode {

    @JsonProperty("name")
    private String name;

    @JsonProperty("path")
    private String relativePath;

    @JsonProperty("isDirectory")
    private boolean directory;

    @JsonProperty("size")
    private long size;

    @JsonProperty("lastModified")
    private long lastModified;

    public FileNode() {
    }

    public FileNode(String name, String relativePath, boolean directory, long size, long lastModified) {
        this.name = name;
        this.relativePath = relativePath;
        this.directory = directory;
        this.size = size;
        this.lastModified = lastModified;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getRelativePath() {
        return relativePath;
    }

    public void setRelativePath(String relativePath) {
        this.relativePath = relativePath;
    }

    public boolean isDirectory() {
        return directory;
    }

    public void setDirectory(boolean directory) {
        this.directory = directory;
    }

    public long getSize() {
        return size;
    }

    public void setSize(long size) {
        this.size = size;
    }

    public long getLastModified() {
        return lastModified;
    }

    public void setLastModified(long lastModified) {
        this.lastModified = lastModified;
    }
}
