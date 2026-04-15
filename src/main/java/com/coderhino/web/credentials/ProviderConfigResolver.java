package com.coderhino.web.credentials;

import com.coderhino.web.settings.SettingsPersistenceService;

/**
 * Resolves provider-specific credentials for the web runtime.
 */
public class ProviderConfigResolver {

    private static final String DEFAULT_MODEL = "MiniMax-M2.5";
    private static final String DEFAULT_BASE_URL = "https://api.anthropic.com";

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
            normalizeBaseUrl(provider.getApiBaseUrl()),
            resolveModel(model, provider)
        );
    }

    private String resolveModel(String requestedModel, ApiCredentials.ApiProvider provider) {
        if (requestedModel != null && !requestedModel.isBlank()) {
            return requestedModel.trim();
        }
        var models = provider.getModels();
        if (models != null && !models.isEmpty()) {
            return models.get(0);
        }
        var settingsModel = settingsService.load().getDefaultModel();
        if (settingsModel != null && !settingsModel.isBlank()) {
            return settingsModel.trim();
        }
        return DEFAULT_MODEL;
    }

    private String normalizeBaseUrl(String rawBaseUrl) {
        if (rawBaseUrl == null || rawBaseUrl.isBlank()) {
            return DEFAULT_BASE_URL;
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

        public ResolvedConfig(String providerId, String apiKey, String baseUrl, String model) {
            this.providerId = providerId;
            this.apiKey = apiKey;
            this.baseUrl = baseUrl;
            this.model = model;
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
    }
}
