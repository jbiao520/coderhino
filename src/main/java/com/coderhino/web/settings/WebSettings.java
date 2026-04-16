package com.coderhino.web.settings;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.ArrayList;
import java.util.List;

/**
 * Non-secret web settings persisted locally and exposed via API.
 * Never contains tokens, API keys, or other secrets.
 */
public class WebSettings {

    @JsonProperty("defaultPermissionMode")
    private String defaultPermissionMode;

    @JsonProperty("theme")
    private String theme;

    @JsonProperty("defaultModel")
    private String defaultModel;

    @JsonProperty("sidebarFontFamily")
    private String sidebarFontFamily;

    @JsonProperty("sidebarFontSize")
    private Integer sidebarFontSize;

    @JsonProperty("chatFontFamily")
    private String chatFontFamily;

    @JsonProperty("chatFontSize")
    private Integer chatFontSize;

    @JsonProperty("referenceSourcePaths")
    private List<String> referenceSourcePaths;

    public WebSettings() {
        this.defaultPermissionMode = "BYPASS";
        this.theme = "system";
        this.defaultModel = null;
        this.sidebarFontFamily = null;
        this.sidebarFontSize = null;
        this.chatFontFamily = null;
        this.chatFontSize = null;
        this.referenceSourcePaths = null;
    }

    public String getDefaultPermissionMode() { return defaultPermissionMode; }
    public void setDefaultPermissionMode(String defaultPermissionMode) {
        this.defaultPermissionMode = defaultPermissionMode;
    }

    public String getTheme() { return theme; }
    public void setTheme(String theme) { this.theme = theme; }

    public String getDefaultModel() { return defaultModel; }
    public void setDefaultModel(String defaultModel) { this.defaultModel = defaultModel; }

    public String getSidebarFontFamily() { return sidebarFontFamily; }
    public void setSidebarFontFamily(String sidebarFontFamily) { this.sidebarFontFamily = sidebarFontFamily; }

    public Integer getSidebarFontSize() { return sidebarFontSize; }
    public void setSidebarFontSize(Integer sidebarFontSize) { this.sidebarFontSize = sidebarFontSize; }

    public String getChatFontFamily() { return chatFontFamily; }
    public void setChatFontFamily(String chatFontFamily) { this.chatFontFamily = chatFontFamily; }

    public Integer getChatFontSize() { return chatFontSize; }
    public void setChatFontSize(Integer chatFontSize) { this.chatFontSize = chatFontSize; }

    public List<String> getReferenceSourcePaths() { return referenceSourcePaths; }
    public void setReferenceSourcePaths(List<String> referenceSourcePaths) {
        this.referenceSourcePaths = referenceSourcePaths == null ? null : new ArrayList<>(referenceSourcePaths);
    }

    public WebSettings copy() {
        var copy = new WebSettings();
        copy.defaultPermissionMode = this.defaultPermissionMode;
        copy.theme = this.theme;
        copy.defaultModel = this.defaultModel;
        copy.sidebarFontFamily = this.sidebarFontFamily;
        copy.sidebarFontSize = this.sidebarFontSize;
        copy.chatFontFamily = this.chatFontFamily;
        copy.chatFontSize = this.chatFontSize;
        copy.referenceSourcePaths = this.referenceSourcePaths == null ? null : new ArrayList<>(this.referenceSourcePaths);
        return copy;
    }
}
