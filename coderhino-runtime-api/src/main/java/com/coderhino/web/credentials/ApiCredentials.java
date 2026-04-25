package com.coderhino.web.credentials;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonProperty.Access;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
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

        public static final long DEFAULT_CONTEXT_WINDOW = 128000L;

        public static final String API_TYPE_CLAUDE_CODE = "CLAUDE_CODE";
        public static final String API_TYPE_OPENAI = "OPENAI";

        @JsonProperty("id")
        private String id;

        @JsonProperty("name")
        private String name;

        @JsonProperty("apiKey")
        private String apiKey;

        @JsonProperty("apiBaseUrl")
        private String apiBaseUrl;

        @JsonProperty("models")
        private List<ModelConfig> models = new ArrayList<>();

        @JsonProperty("apiType")
        private String apiType = API_TYPE_CLAUDE_CODE;

        public ApiProvider() {
        }

        public ApiProvider(String id, String name, String apiKey, String apiBaseUrl, List<ModelConfig> models) {
            this(id, name, apiKey, apiBaseUrl, models, API_TYPE_CLAUDE_CODE);
        }

        public ApiProvider(String id, String name, String apiKey, String apiBaseUrl, List<ModelConfig> models, String apiType) {
            this.id = id;
            this.name = name;
            this.apiKey = apiKey;
            this.apiBaseUrl = apiBaseUrl;
            setModels(models);
            setApiType(apiType);
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

        public List<ModelConfig> getModels() {
            return models;
        }

        public void setModels(List<ModelConfig> models) {
            this.models = new ArrayList<>();
            if (models == null) {
                return;
            }
            for (var model : models) {
                var normalized = ModelConfig.normalize(model);
                if (normalized != null) {
                    this.models.add(normalized);
                }
            }
        }

        @JsonProperty("models")
        void readModelsFromJson(List<JsonNode> rawModels) {
            this.models = new ArrayList<>();
            if (rawModels == null) {
                return;
            }
            for (var rawModel : rawModels) {
                var normalized = ModelConfig.fromJson(rawModel);
                if (normalized != null) {
                    this.models.add(normalized);
                }
            }
        }

        @JsonIgnore
        public List<String> getModelIds() {
            return models.stream().map(ModelConfig::getId).toList();
        }

        @JsonIgnore
        public ModelConfig findModel(String modelId) {
            if (modelId == null || models == null) {
                return null;
            }
            for (var model : models) {
                if (model != null && Objects.equals(modelId, model.getId())) {
                    return model;
                }
            }
            return null;
        }

        public String getApiType() {
            return apiType;
        }

        public void setApiType(String apiType) {
            this.apiType = normalizeApiType(apiType);
        }

        public static String normalizeApiType(String apiType) {
            if (apiType == null || apiType.isBlank()) {
                return API_TYPE_CLAUDE_CODE;
            }
            var normalized = apiType.trim().toUpperCase(Locale.ROOT).replace('-', '_');
            if (API_TYPE_OPENAI.equals(normalized)) {
                return API_TYPE_OPENAI;
            }
            return API_TYPE_CLAUDE_CODE;
        }

        public static class ModelConfig {

            @JsonProperty("id")
            private String id;

            @JsonProperty("contextWindow")
            private long contextWindow = DEFAULT_CONTEXT_WINDOW;

            public ModelConfig() {
            }

            public ModelConfig(String id, Long contextWindow) {
                setId(id);
                setContextWindow(contextWindow);
            }

            public String getId() {
                return id;
            }

            public void setId(String id) {
                this.id = blankToNull(id);
            }

            public long getContextWindow() {
                return contextWindow;
            }

            public void setContextWindow(Long contextWindow) {
                this.contextWindow = normalizeContextWindow(contextWindow);
            }

            public static long normalizeContextWindow(Long contextWindow) {
                if (contextWindow == null || contextWindow <= 0) {
                    return DEFAULT_CONTEXT_WINDOW;
                }
                return contextWindow;
            }

            static ModelConfig normalize(ModelConfig model) {
                if (model == null) {
                    return null;
                }
                var id = blankToNull(model.getId());
                if (id == null) {
                    return null;
                }
                return new ModelConfig(id, model.getContextWindow());
            }

            static ModelConfig fromJson(JsonNode node) {
                if (node == null || node.isNull()) {
                    return null;
                }
                if (node.isTextual()) {
                    return new ModelConfig(node.asText(), DEFAULT_CONTEXT_WINDOW);
                }
                if (!node.isObject()) {
                    return null;
                }
                var idNode = node.get("id");
                var id = idNode == null ? null : idNode.asText(null);
                Long contextWindow = null;
                var contextWindowNode = node.get("contextWindow");
                if (contextWindowNode != null && contextWindowNode.canConvertToLong()) {
                    contextWindow = contextWindowNode.asLong();
                }
                return normalize(new ModelConfig(id, contextWindow));
            }
        }
    }
}
