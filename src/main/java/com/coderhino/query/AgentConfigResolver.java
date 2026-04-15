package com.coderhino.query;

import com.coderhino.web.credentials.ApiCredentials;
import com.coderhino.web.credentials.CredentialsPersistenceService;
import com.coderhino.web.settings.SettingsPersistenceService;
import com.coderhino.web.settings.WebSettings;

/**
 * Resolves configuration for {@link AgentModelClient} from persisted credentials and settings.
 * <p>
 * This is a plain Java class (not Spring-managed) that can be constructed with explicit
 * service instances for testability, or using the default constructor which creates
 * its own service instances using default file paths.
 * <p>
 * Configuration precedence for the selected default provider:
 * <ul>
 *   <li>Model: defaultProvider.models[0] &gt; settings.defaultModel &gt; "MiniMax-M2.5"</li>
 *   <li>Base URL: defaultProvider.apiBaseUrl (normalized) or "https://api.anthropic.com"</li>
 *   <li>API key: defaultProvider.apiKey (required, throws if missing/blank)</li>
 * </ul>
 */
public class AgentConfigResolver {

    private static final String DEFAULT_MODEL = "MiniMax-M2.5";
    private static final String DEFAULT_BASE_URL = "https://api.anthropic.com";

    private final CredentialsPersistenceService credentialsService;
    private final SettingsPersistenceService settingsService;

    /**
     * Default constructor using default file paths for credentials and settings.
     */
    public AgentConfigResolver() {
        this.credentialsService = new CredentialsPersistenceService();
        this.settingsService = new SettingsPersistenceService();
    }

    /**
     * Constructor for testing with explicit service instances.
     *
     * @param credentialsService service for loading credentials
     * @param settingsService    service for loading settings
     */
    public AgentConfigResolver(CredentialsPersistenceService credentialsService,
                               SettingsPersistenceService settingsService) {
        this.credentialsService = credentialsService;
        this.settingsService = settingsService;
    }

    /**
     * Resolves configuration from persisted credentials and settings.
     *
     * @return resolved configuration
     * @throws IllegalStateException if API key is missing or blank
     */
    public ResolvedConfig resolve() {
        ApiCredentials credentials = credentialsService.load();
        WebSettings settings = settingsService.load();
        var provider = credentials.getDefaultProvider();

        if (provider == null) {
            throw new IllegalStateException(
                "A default API provider is required but not configured. " +
                "Please add a provider in the web UI settings or via the credentials persistence file."
            );
        }

        // Resolve API key (required)
        String apiKey = provider.getApiKey();
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException(
                "An API key is required for the default provider but is not configured. " +
                "Please set the API key in the web UI settings or via the credentials persistence file."
            );
        }

        // Resolve base URL with normalization
        String baseUrl = normalizeBaseUrl(provider.getApiBaseUrl());

        // Resolve model with precedence: defaultProvider.models[0] > settings.defaultModel > DEFAULT_MODEL
        String model = resolveModel(provider, settings.getDefaultModel());

        return new ResolvedConfig(apiKey, baseUrl, model);
    }

    /**
     * Normalizes base URL: defaults to DEFAULT_BASE_URL if blank, trims whitespace,
     * and strips trailing slashes.
     *
     * @param rawBaseUrl raw base URL from credentials (may be null/blank)
     * @return normalized base URL
     */
    private String normalizeBaseUrl(String rawBaseUrl) {
        if (rawBaseUrl == null || rawBaseUrl.isBlank()) {
            return DEFAULT_BASE_URL;
        }

        String normalized = rawBaseUrl.trim();
        
        // Strip trailing slashes
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }

        return normalized;
    }

    /**
     * Resolves model with precedence: credentialsModel > settingsModel > DEFAULT_MODEL.
     *
     * @param credentialsModel model from credentials (may be null/blank)
     * @param settingsModel    model from settings (may be null/blank)
     * @return resolved model
     */
    private String resolveModel(ApiCredentials.ApiProvider provider, String settingsModel) {
        if (provider != null && provider.getModels() != null && !provider.getModels().isEmpty()) {
            var providerModel = provider.getModels().get(0);
            if (providerModel != null && !providerModel.isBlank()) {
                return providerModel.trim();
            }
        }
        if (settingsModel != null && !settingsModel.isBlank()) {
            return settingsModel.trim();
        }
        return DEFAULT_MODEL;
    }

    /**
     * Resolved configuration for AgentModelClient.
     */
    public static class ResolvedConfig {
        private final String apiKey;
        private final String baseUrl;
        private final String model;

        public ResolvedConfig(String apiKey, String baseUrl, String model) {
            this.apiKey = apiKey;
            this.baseUrl = baseUrl;
            this.model = model;
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
