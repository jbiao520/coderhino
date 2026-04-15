package com.coderhino.web.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.ArrayList;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class FileTreeDto {

    public enum NodeType {
        FILE, DIRECTORY
    }

    @JsonProperty("name")
    private String name;

    @JsonProperty("type")
    private NodeType type;

    @JsonProperty("path")
    private String path;

    @JsonProperty("children")
    private List<FileTreeDto> children;

    public FileTreeDto() {
    }

    public FileTreeDto(String name, NodeType type, String path) {
        this.name = name;
        this.type = type;
        this.path = path;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public NodeType getType() {
        return type;
    }

    public void setType(NodeType type) {
        this.type = type;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public List<FileTreeDto> getChildren() {
        return children;
    }

    public void setChildren(List<FileTreeDto> children) {
        this.children = children;
    }

    public void addChild(FileTreeDto child) {
        if (this.children == null) {
            this.children = new ArrayList<>();
        }
        this.children.add(child);
    }
}
