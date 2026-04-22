package com.coderhino.web.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public class FileContentDto {

    @JsonProperty("content")
    private String content;

    @JsonProperty("size")
    private long size;

    @JsonProperty("mimeType")
    private String mimeType;

    @JsonProperty("path")
    private String path;

    public FileContentDto() {
    }

    public FileContentDto(String content, long size, String mimeType, String path) {
        this.content = content;
        this.size = size;
        this.mimeType = mimeType;
        this.path = path;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public long getSize() {
        return size;
    }

    public void setSize(long size) {
        this.size = size;
    }

    public String getMimeType() {
        return mimeType;
    }

    public void setMimeType(String mimeType) {
        this.mimeType = mimeType;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }
}
