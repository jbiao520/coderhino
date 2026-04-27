package com.coderhino.web.credentials;

import com.coderhino.config.credentials.ApiCredentials;
import com.coderhino.config.credentials.CredentialsPersistenceService;
import com.coderhino.config.settings.SettingsPersistenceService;
import com.coderhino.query.ProviderApiType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ProviderConfigResolverTest {

    @Test
    void resolveSupportsOpenAiProviderWithDefaultBaseUrl(@TempDir Path tempDir) {
        var credentialsService = new CredentialsPersistenceService(tempDir.resolve("api-credentials.json"));
        var settingsService = new SettingsPersistenceService(tempDir.resolve("web-settings.json"));
        var credentials = new ApiCredentials();
        credentials.setDefaultProviderId("provider-1");
        credentials.setProviders(List.of(
            new ApiCredentials.ApiProvider(
                "provider-1",
                "OpenAI",
                "secret",
                null,
                List.of(new ApiCredentials.ApiProvider.ModelConfig("gpt-4o", 128000L)),
                ApiCredentials.ApiProvider.API_TYPE_OPENAI
            )
        ));
        credentialsService.save(credentials);

        var config = new ProviderConfigResolver(credentialsService, settingsService).resolve(null, null);

        assertEquals(ProviderApiType.OPENAI, config.getApiType());
        assertEquals("https://api.openai.com", config.getBaseUrl());
        assertEquals("gpt-4o", config.getModel());
    }
}
