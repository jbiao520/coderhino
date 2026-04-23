package com.coderhino.web.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.ArrayList;
import java.util.List;

/**
 * DTO for API credentials responses. Always returns masked API keys.
 */
public class CredentialsDto {

    @JsonProperty("defaultProviderId")
    private String defaultProviderId;

    @JsonProperty("providers")
    private List<ProviderDto> providers = new ArrayList<>();

    public CredentialsDto() {
    }

    public CredentialsDto(String defaultProviderId, List<ProviderDto> providers) {
        this.defaultProviderId = defaultProviderId;
        this.providers = providers == null ? new ArrayList<>() : new ArrayList<>(providers);
    }

    public String getDefaultProviderId() {
        return defaultProviderId;
    }

    public void setDefaultProviderId(String defaultProviderId) {
        this.defaultProviderId = defaultProviderId;
    }

    public List<ProviderDto> getProviders() {
        return providers;
    }

    public void setProviders(List<ProviderDto> providers) {
        this.providers = providers == null ? new ArrayList<>() : new ArrayList<>(providers);
    }

    public static class ProviderDto {

        @JsonProperty("id")
        private String id;

        @JsonProperty("name")
        private String name;

        @JsonProperty("apiKeyMasked")
        private String apiKeyMasked;

        @JsonProperty("apiBaseUrl")
        private String apiBaseUrl;

        @JsonProperty("models")
        private List<ModelDto> models = new ArrayList<>();

        @JsonProperty("apiType")
        private String apiType;

        @JsonProperty("hasApiKey")
        private boolean hasApiKey;

        public ProviderDto() {
        }

        public ProviderDto(String id, String name, String apiKeyMasked, String apiBaseUrl, List<ModelDto> models, String apiType, boolean hasApiKey) {
            this.id = id;
            this.name = name;
            this.apiKeyMasked = apiKeyMasked;
            this.apiBaseUrl = apiBaseUrl;
            this.models = models == null ? new ArrayList<>() : new ArrayList<>(models);
            this.apiType = apiType;
            this.hasApiKey = hasApiKey;
        }

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getApiKeyMasked() {
            return apiKeyMasked;
        }

        public void setApiKeyMasked(String apiKeyMasked) {
            this.apiKeyMasked = apiKeyMasked;
        }

        public String getApiBaseUrl() {
            return apiBaseUrl;
        }

        public void setApiBaseUrl(String apiBaseUrl) {
            this.apiBaseUrl = apiBaseUrl;
        }

        public List<ModelDto> getModels() {
            return models;
        }

        public void setModels(List<ModelDto> models) {
            this.models = models == null ? new ArrayList<>() : new ArrayList<>(models);
        }

        public String getApiType() {
            return apiType;
        }

        public void setApiType(String apiType) {
            this.apiType = apiType;
        }

        public boolean isHasApiKey() {
            return hasApiKey;
        }

        public void setHasApiKey(boolean hasApiKey) {
            this.hasApiKey = hasApiKey;
        }

        public static class ModelDto {

            @JsonProperty("id")
            private String id;

            @JsonProperty("contextWindow")
            private long contextWindow;

            public ModelDto() {
            }

            public ModelDto(String id, long contextWindow) {
                this.id = id;
                this.contextWindow = contextWindow;
            }

            public String getId() {
                return id;
            }

            public void setId(String id) {
                this.id = id;
            }

            public long getContextWindow() {
                return contextWindow;
            }

            public void setContextWindow(long contextWindow) {
                this.contextWindow = contextWindow;
            }
        }
    }
}
