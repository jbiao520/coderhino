package com.coderhino.web.credentials;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonProperty.Access;
import com.fasterxml.jackson.annotation.JsonIgnore;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * API credentials persisted locally in a separate file from WebSettings.
 * Stored at .coderhino/api-credentials.json
 */
public class ApiCredentials {

    @JsonProperty("defaultProviderId")
    private String defaultProviderId;

    @JsonProperty("providers")
    private List<ApiProvider> providers = new ArrayList<>();

    @JsonProperty(value = "apiKey", access = Access.WRITE_ONLY)
    private String legacyApiKey;

    @JsonProperty(value = "apiBaseUrl", access = Access.WRITE_ONLY)
    private String legacyApiBaseUrl;

    @JsonProperty(value = "model", access = Access.WRITE_ONLY)
    private String legacyModel;

    public ApiCredentials() {
    }

    public String getDefaultProviderId() {
        return defaultProviderId;
    }

    public void setDefaultProviderId(String defaultProviderId) {
        this.defaultProviderId = blankToNull(defaultProviderId);
    }

    public List<ApiProvider> getProviders() {
        return providers;
    }

    public void setProviders(List<ApiProvider> providers) {
        this.providers = providers == null ? new ArrayList<>() : new ArrayList<>(providers);
    }

    public String getLegacyApiKey() {
        return legacyApiKey;
    }

    public String getLegacyApiBaseUrl() {
        return legacyApiBaseUrl;
    }

    public String getLegacyModel() {
        return legacyModel;
    }

    @JsonIgnore
    public boolean hasProviders() {
        return providers != null && !providers.isEmpty();
    }

    @JsonIgnore
    public ApiProvider getDefaultProvider() {
        if (!hasProviders()) {
            return null;
        }
        if (defaultProviderId != null) {
            for (var provider : providers) {
                if (Objects.equals(defaultProviderId, provider.getId())) {
                    return provider;
                }
            }
        }
        return providers.get(0);
    }

    @JsonIgnore
    public ApiProvider findProvider(String providerId) {
        if (providerId == null || providers == null) {
            return null;
        }
        for (var provider : providers) {
            if (Objects.equals(providerId, provider.getId())) {
                return provider;
            }
        }
        return null;
    }

    private static String blankToNull(String value) {
        if (value == null) {
            return null;
        }
        var trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    public static class ApiProvider {

        @JsonProperty("id")
        private String id;

        @JsonProperty("name")
        private String name;

        @JsonProperty("apiKey")
        private String apiKey;

        @JsonProperty("apiBaseUrl")
        private String apiBaseUrl;

        @JsonProperty("models")
        private List<String> models = new ArrayList<>();

        public ApiProvider() {
        }

        public ApiProvider(String id, String name, String apiKey, String apiBaseUrl, List<String> models) {
            this.id = id;
            this.name = name;
            this.apiKey = apiKey;
            this.apiBaseUrl = apiBaseUrl;
            setModels(models);
        }

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = blankToNull(id);
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = blankToNull(name);
        }

        public String getApiKey() {
            return apiKey;
        }

        public void setApiKey(String apiKey) {
            this.apiKey = blankToNull(apiKey);
        }

        public String getApiBaseUrl() {
            return apiBaseUrl;
        }

        public void setApiBaseUrl(String apiBaseUrl) {
            this.apiBaseUrl = blankToNull(apiBaseUrl);
        }

        public List<String> getModels() {
            return models;
        }

        public void setModels(List<String> models) {
            this.models = new ArrayList<>();
            if (models == null) {
                return;
            }
            for (var model : models) {
                var trimmed = blankToNull(model);
                if (trimmed != null) {
                    this.models.add(trimmed);
                }
            }
        }
    }
}
