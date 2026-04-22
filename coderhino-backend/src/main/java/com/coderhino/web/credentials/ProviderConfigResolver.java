package com.coderhino.web.credentials;

import com.coderhino.query.ProviderApiType;
import com.coderhino.web.settings.SettingsPersistenceService;

/**
 * Resolves provider-specific credentials for the web runtime.
 */
public class ProviderConfigResolver {

    private static final String DEFAULT_MODEL = "MiniMax-M2.5";
    private static final ProviderApiType DEFAULT_API_TYPE = ProviderApiType.CLAUDE_CODE;
    private static final long DEFAULT_CONTEXT_WINDOW = 128000L;

    private final CredentialsPersistenceService credentialsService;
    private final SettingsPersistenceService settingsService;

    public ProviderConfigResolver() {
        this(new CredentialsPersistenceService(), new SettingsPersistenceService());
    }

    public ProviderConfigResolver(CredentialsPersistenceService credentialsService, SettingsPersistenceService settingsService) {
        this.credentialsService = credentialsService;
        this.settingsService = settingsService;
    }

    public ResolvedConfig resolve(String providerId, String model) {
        var credentials = credentialsService.load();
        var provider = providerId != null ? credentials.findProvider(providerId) : null;
        if (providerId != null && !providerId.isBlank() && provider == null) {
            throw new IllegalStateException("Configured provider '" + providerId + "' is no longer available.");
        }
        if (provider == null) {
            provider = credentials.getDefaultProvider();
        }
        if (provider == null) {
            throw new IllegalStateException("No API provider is configured. Please add a provider in settings.");
        }
        if (provider.getApiKey() == null || provider.getApiKey().isBlank()) {
            throw new IllegalStateException("API key is missing for provider '" + provider.getName() + "'.");
        }
        return new ResolvedConfig(
            provider.getId(),
            provider.getApiKey(),
            normalizeBaseUrl(provider.getApiBaseUrl(), ProviderApiType.fromStoredValue(provider.getApiType())),
            resolveModel(model, provider),
            ProviderApiType.fromStoredValue(provider.getApiType()),
            resolveContextWindow(resolveModel(model, provider), provider)
        );
    }

    private String resolveModel(String requestedModel, ApiCredentials.ApiProvider provider) {
        if (requestedModel != null && !requestedModel.isBlank()) {
            return requestedModel.trim();
        }
        var models = provider.getModels();
        if (models != null && !models.isEmpty()) {
            return models.get(0).getId();
        }
        var settingsModel = settingsService.load().getDefaultModel();
        if (settingsModel != null && !settingsModel.isBlank()) {
            return settingsModel.trim();
        }
        return DEFAULT_MODEL;
    }

    private long resolveContextWindow(String requestedModel, ApiCredentials.ApiProvider provider) {
        if (provider == null || requestedModel == null || requestedModel.isBlank()) {
            return DEFAULT_CONTEXT_WINDOW;
        }
        var model = provider.findModel(requestedModel);
        if (model == null) {
            return DEFAULT_CONTEXT_WINDOW;
        }
        return ApiCredentials.ApiProvider.ModelConfig.normalizeContextWindow(model.getContextWindow());
    }

    private String normalizeBaseUrl(String rawBaseUrl, ProviderApiType apiType) {
        if (rawBaseUrl == null || rawBaseUrl.isBlank()) {
            return (apiType == null ? DEFAULT_API_TYPE : apiType).defaultBaseUrl();
        }
        var normalized = rawBaseUrl.trim();
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }

    public static class ResolvedConfig {
        private final String providerId;
        private final String apiKey;
        private final String baseUrl;
        private final String model;
        private final ProviderApiType apiType;
        private final long contextWindow;

        public ResolvedConfig(String providerId, String apiKey, String baseUrl, String model, ProviderApiType apiType, long contextWindow) {
            this.providerId = providerId;
            this.apiKey = apiKey;
            this.baseUrl = baseUrl;
            this.model = model;
            this.apiType = apiType;
            this.contextWindow = contextWindow;
        }

        public String getProviderId() {
            return providerId;
        }

        public String getApiKey() {
            return apiKey;
        }

        public String getBaseUrl() {
            return baseUrl;
        }

        public String getModel() {
            return model;
        }

        public ProviderApiType getApiType() {
            return apiType;
        }

        public long getContextWindow() {
            return contextWindow;
        }
    }
}
