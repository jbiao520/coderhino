package com.coderhino.agent.spring;

import com.coderhino.types.PermissionMode;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

@ConfigurationProperties(prefix = "coderhino.agent")
public class CoderhinoAgentProperties {
    private String model = "sonnet";
    private Path cwd = Path.of("").toAbsolutePath().normalize();
    private PermissionMode permissionMode = PermissionMode.DEFAULT;
    private List<String> enabledTools = new ArrayList<>();
    private String customSystemPrompt;
    private String appendSystemPrompt;
    private int maxToolIterations = 200;
    private double maxBudgetUsd = 0.0;
    private boolean embeddedIntegrationsEnabled = false;
    private String apiKey;
    private String apiBaseUrl = "https://api.anthropic.com";
    private ProviderApiType providerApiType = ProviderApiType.CLAUDE_CODE;
    private long contextWindow = 128000L;

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        if (model != null && !model.isBlank()) {
            this.model = model;
        }
    }

    public Path getCwd() {
        return cwd;
    }

    public void setCwd(Path cwd) {
        if (cwd != null) {
            this.cwd = cwd.toAbsolutePath().normalize();
        }
    }

    public PermissionMode getPermissionMode() {
        return permissionMode;
    }

    public void setPermissionMode(PermissionMode permissionMode) {
        if (permissionMode != null) {
            this.permissionMode = permissionMode;
        }
    }

    public List<String> getEnabledTools() {
        return enabledTools;
    }

    public void setEnabledTools(List<String> enabledTools) {
        this.enabledTools = enabledTools == null ? new ArrayList<>() : new ArrayList<>(enabledTools);
    }

    public String getCustomSystemPrompt() {
        return customSystemPrompt;
    }

    public void setCustomSystemPrompt(String customSystemPrompt) {
        this.customSystemPrompt = customSystemPrompt;
    }

    public String getAppendSystemPrompt() {
        return appendSystemPrompt;
    }

    public void setAppendSystemPrompt(String appendSystemPrompt) {
        this.appendSystemPrompt = appendSystemPrompt;
    }

    public int getMaxToolIterations() {
        return maxToolIterations;
    }

    public void setMaxToolIterations(int maxToolIterations) {
        this.maxToolIterations = maxToolIterations;
    }

    public double getMaxBudgetUsd() {
        return maxBudgetUsd;
    }

    public void setMaxBudgetUsd(double maxBudgetUsd) {
        this.maxBudgetUsd = maxBudgetUsd;
    }

    public boolean isEmbeddedIntegrationsEnabled() {
        return embeddedIntegrationsEnabled;
    }

    public void setEmbeddedIntegrationsEnabled(boolean embeddedIntegrationsEnabled) {
        this.embeddedIntegrationsEnabled = embeddedIntegrationsEnabled;
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public String getApiBaseUrl() {
        return apiBaseUrl;
    }

    public void setApiBaseUrl(String apiBaseUrl) {
        if (apiBaseUrl != null && !apiBaseUrl.isBlank()) {
            this.apiBaseUrl = apiBaseUrl;
        }
    }

    public ProviderApiType getProviderApiType() {
        return providerApiType;
    }

    public void setProviderApiType(ProviderApiType providerApiType) {
        if (providerApiType != null) {
            this.providerApiType = providerApiType;
        }
    }

    public long getContextWindow() {
        return contextWindow;
    }

    public void setContextWindow(long contextWindow) {
        this.contextWindow = contextWindow;
    }

    public enum ProviderApiType {
        CLAUDE_CODE,
        OPENAI;

        com.coderhino.query.ProviderApiType toRuntimeType() {
            return this == OPENAI ? com.coderhino.query.ProviderApiType.OPENAI : com.coderhino.query.ProviderApiType.CLAUDE_CODE;
        }
    }
}
