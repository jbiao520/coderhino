package com.coderhino.web.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public class FolderSearchRequest {

    @JsonProperty("query")
    private String query;

    @JsonProperty("maxResults")
    private int maxResults;

    public FolderSearchRequest() {
        this.maxResults = 50;
    }

    public FolderSearchRequest(String query, int maxResults) {
        this.query = query;
        this.maxResults = maxResults;
    }

    public String getQuery() {
        return query;
    }

    public void setQuery(String query) {
        this.query = query;
    }

    public int getMaxResults() {
        return maxResults;
    }

    public void setMaxResults(int maxResults) {
        this.maxResults = maxResults;
    }
}
