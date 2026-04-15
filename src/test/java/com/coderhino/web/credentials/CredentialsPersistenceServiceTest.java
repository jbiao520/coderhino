package com.coderhino.web.credentials;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CredentialsPersistenceServiceTest {

    @Test
    void loadMigratesLegacySingleProviderShape(@TempDir Path tempDir) throws Exception {
        var file = tempDir.resolve("api-credentials.json");
        Files.writeString(file, """
            {
              "apiKey": "legacy-secret",
              "apiBaseUrl": "https://api.legacy.example/v1",
              "model": "legacy-model"
            }
            """);

        var service = new CredentialsPersistenceService(file);

        var credentials = service.load();

        assertEquals("provider-legacy", credentials.getDefaultProviderId());
        assertEquals(1, credentials.getProviders().size());
        var provider = credentials.getProviders().get(0);
        assertEquals("provider-legacy", provider.getId());
        assertEquals("Migrated Provider", provider.getName());
        assertEquals("legacy-secret", provider.getApiKey());
        assertEquals("https://api.legacy.example/v1", provider.getApiBaseUrl());
        assertEquals(List.of("legacy-model"), provider.getModels());
    }

    @Test
    void saveWritesOnlyNewProviderCollectionShape(@TempDir Path tempDir) throws Exception {
        var file = tempDir.resolve("api-credentials.json");
        var service = new CredentialsPersistenceService(file);
        var credentials = new ApiCredentials();
        credentials.setDefaultProviderId("provider-1");
        credentials.setProviders(List.of(
            new ApiCredentials.ApiProvider(
                "provider-1",
                "Anthropic",
                "secret-1234",
                "https://api.anthropic.com",
                List.of("MiniMax-M2.5", "GPT-5.4")
            )
        ));

        service.save(credentials);

        var raw = Files.readString(file);
        assertTrue(raw.contains("\"providers\""));
        assertTrue(raw.contains("\"defaultProviderId\""));
        assertFalse(raw.contains("\"model\" : \"secret-1234\""));
        assertFalse(raw.contains("\"apiKey\" : \"\""));

        var reloaded = service.load();
        assertEquals(List.of("MiniMax-M2.5", "GPT-5.4"), reloaded.getProviders().get(0).getModels());
    }

    @Test
    void loadAssignsDefaultProviderWhenMissing(@TempDir Path tempDir) {
        var file = tempDir.resolve("api-credentials.json");
        var service = new CredentialsPersistenceService(file);
        var credentials = new ApiCredentials();
        credentials.setProviders(List.of(
            new ApiCredentials.ApiProvider("provider-a", "A", null, null, List.of()),
            new ApiCredentials.ApiProvider("provider-b", "B", null, null, List.of())
        ));

        service.save(credentials);

        var reloaded = service.load();
        assertNotNull(reloaded.getDefaultProviderId());
        assertEquals("provider-a", reloaded.getDefaultProviderId());
    }
}
