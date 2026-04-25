package com.coderhino.config.credentials;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class CredentialsPersistenceService {

    private static final Logger log = LoggerFactory.getLogger(CredentialsPersistenceService.class);
    private static final String SETTINGS_DIR = ".coderhino";
    private static final String CREDENTIALS_FILE = "api-credentials.json";

    private final ObjectMapper objectMapper;
    private final Path credentialsFile;

    public CredentialsPersistenceService() {
        this(Path.of("").toAbsolutePath().normalize().resolve(SETTINGS_DIR).resolve(CREDENTIALS_FILE));
    }

    public CredentialsPersistenceService(Path credentialsFile) {
        this.credentialsFile = credentialsFile;
        this.objectMapper = new ObjectMapper()
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    public ApiCredentials load() {
        if (!Files.exists(credentialsFile)) {
            return new ApiCredentials();
        }
        try {
            return normalize(objectMapper.readValue(credentialsFile.toFile(), ApiCredentials.class));
        } catch (IOException e) {
            log.warn("Failed to load credentials from {}: {}", credentialsFile, e.getMessage());
            return new ApiCredentials();
        }
    }

    public void save(ApiCredentials credentials) {
        try {
            var dir = credentialsFile.getParent();
            if (!Files.exists(dir)) {
                Files.createDirectories(dir);
            }
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(credentialsFile.toFile(), normalize(credentials));
        } catch (IOException e) {
            log.error("Failed to save credentials to {}: {}", credentialsFile, e.getMessage());
            throw new RuntimeException("Cannot save credentials: " + credentialsFile, e);
        }
    }

    private ApiCredentials normalize(ApiCredentials credentials) {
        var normalized = credentials == null ? new ApiCredentials() : credentials;
        var providers = new ArrayList<ApiCredentials.ApiProvider>();
        if (normalized.getProviders() != null) {
            for (var provider : normalized.getProviders()) {
                if (provider == null) {
                    continue;
                }
                var cleaned = normalizeProvider(provider, providers.size() + 1);
                if (cleaned != null) {
                    providers.add(cleaned);
                }
            }
        }
        if (providers.isEmpty() && hasLegacyValues(normalized)) {
            providers.add(createLegacyProvider(normalized));
        }
        normalized.setProviders(providers);
        var defaultProvider = normalized.findProvider(normalized.getDefaultProviderId());
        if (defaultProvider == null && !providers.isEmpty()) {
            normalized.setDefaultProviderId(providers.get(0).getId());
        }
        return normalized;
    }

    private boolean hasLegacyValues(ApiCredentials credentials) {
        return isNotBlank(credentials.getLegacyApiKey())
            || isNotBlank(credentials.getLegacyApiBaseUrl())
            || isNotBlank(credentials.getLegacyModel());
    }

    private ApiCredentials.ApiProvider createLegacyProvider(ApiCredentials credentials) {
        var models = new ArrayList<ApiCredentials.ApiProvider.ModelConfig>();
        if (isNotBlank(credentials.getLegacyModel())) {
            models.add(new ApiCredentials.ApiProvider.ModelConfig(
                credentials.getLegacyModel().trim(),
                ApiCredentials.ApiProvider.DEFAULT_CONTEXT_WINDOW
            ));
        }
        return new ApiCredentials.ApiProvider(
            "provider-legacy",
            "Migrated Provider",
            credentials.getLegacyApiKey(),
            credentials.getLegacyApiBaseUrl(),
            models,
            ApiCredentials.ApiProvider.API_TYPE_CLAUDE_CODE
        );
    }

    private ApiCredentials.ApiProvider normalizeProvider(ApiCredentials.ApiProvider provider, int index) {
        var name = isNotBlank(provider.getName()) ? provider.getName().trim() : "Provider " + index;
        var models = provider.getModels() == null ? List.<ApiCredentials.ApiProvider.ModelConfig>of() : provider.getModels();
        if (!isNotBlank(provider.getId()) && !isNotBlank(name) && !isNotBlank(provider.getApiBaseUrl())
            && !isNotBlank(provider.getApiKey()) && models.isEmpty()) {
            return null;
        }
        return new ApiCredentials.ApiProvider(
            isNotBlank(provider.getId()) ? provider.getId().trim() : "provider-" + UUID.randomUUID(),
            name,
            provider.getApiKey(),
            provider.getApiBaseUrl(),
            models,
            provider.getApiType()
        );
    }

    private boolean isNotBlank(String value) {
        return value != null && !value.isBlank();
    }

    public String maskApiKey(String apiKey) {
        if (apiKey == null || apiKey.isEmpty()) {
            return null;
        }
        if (apiKey.length() <= 4) {
            return "****";
        }
        return "****" + apiKey.substring(apiKey.length() - 4);
    }
}
