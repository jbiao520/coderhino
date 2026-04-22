package com.coderhino.web.mcp;

import com.fasterxml.jackson.annotation.JsonProperty;

public class McpConfigDocument {

    @JsonProperty("content")
    private String content;

    public McpConfigDocument() {
    }

    public McpConfigDocument(String content) {
        this.content = content;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }
}
