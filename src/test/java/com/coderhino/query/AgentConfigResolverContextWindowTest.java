package com.coderhino.query;

import com.coderhino.web.credentials.ApiCredentials;
import com.coderhino.web.credentials.CredentialsPersistenceService;
import com.coderhino.web.settings.SettingsPersistenceService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AgentConfigResolverContextWindowTest {

    @Test
    void resolveUsesConfiguredModelContextWindow(@TempDir Path tempDir) {
        var credentialsService = new CredentialsPersistenceService(tempDir.resolve("api-credentials.json"));
        var settingsService = new SettingsPersistenceService(tempDir.resolve("web-settings.json"));
        var credentials = new ApiCredentials();
        credentials.setDefaultProviderId("provider-1");
        credentials.setProviders(List.of(
            new ApiCredentials.ApiProvider(
                "provider-1",
                "Anthropic",
                "secret",
                "https://api.anthropic.com",
                List.of(new ApiCredentials.ApiProvider.ModelConfig("MiniMax-M2.5", 64000L))
            )
        ));
        credentialsService.save(credentials);

        var resolver = new AgentConfigResolver(credentialsService, settingsService);
        var config = resolver.resolve();

        assertEquals("MiniMax-M2.5", config.getModel());
        assertEquals(64000L, config.getContextWindow());
    }

    @Test
    void resolveFallsBackToDefaultContextWindowWhenModelMetadataMissing(@TempDir Path tempDir) {
        var credentialsService = new CredentialsPersistenceService(tempDir.resolve("api-credentials.json"));
        var settingsService = new SettingsPersistenceService(tempDir.resolve("web-settings.json"));
        var credentials = new ApiCredentials();
        credentials.setDefaultProviderId("provider-1");
        credentials.setProviders(List.of(
            new ApiCredentials.ApiProvider(
                "provider-1",
                "Anthropic",
                "secret",
                "https://api.anthropic.com",
                List.of(new ApiCredentials.ApiProvider.ModelConfig("MiniMax-M2.5", 64000L))
            )
        ));
        credentialsService.save(credentials);
        var settings = new com.coderhino.web.settings.WebSettings();
        settings.setDefaultModel("custom-model");
        settingsService.save(settings);

        var resolver = new AgentConfigResolver(credentialsService, settingsService);
        var config = resolver.resolve();

        assertEquals("MiniMax-M2.5", config.getModel());
        assertEquals(64000L, config.getContextWindow());
    }
}
