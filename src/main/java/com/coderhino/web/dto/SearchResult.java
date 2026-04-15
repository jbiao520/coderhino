package com.coderhino.web.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public class SearchResult {

    public enum MatchType {
        EXACT, CONTAINS, STARTS_WITH
    }

    @JsonProperty("path")
    private String path;

    @JsonProperty("name")
    private String name;

    @JsonProperty("matchType")
    private MatchType matchType;

    public SearchResult() {
    }

    public SearchResult(String path, String name, MatchType matchType) {
        this.path = path;
        this.name = name;
        this.matchType = matchType;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public MatchType getMatchType() {
        return matchType;
    }

    public void setMatchType(MatchType matchType) {
        this.matchType = matchType;
    }
}
