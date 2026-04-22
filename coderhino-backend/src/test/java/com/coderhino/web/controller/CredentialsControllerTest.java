package com.coderhino.web.controller;

import com.coderhino.web.credentials.ApiCredentials;
import com.coderhino.web.credentials.CredentialsPersistenceService;
import com.coderhino.web.dto.CredentialsDto;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.HttpStatus;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CredentialsControllerTest {

    private static ApiCredentials.ApiProvider.ModelConfig model(String id) {
        return new ApiCredentials.ApiProvider.ModelConfig(id, 128000L);
    }

    private static ApiCredentials.ApiProvider.ModelConfig model(String id, long contextWindow) {
        return new ApiCredentials.ApiProvider.ModelConfig(id, contextWindow);
    }

    @Test
    void getCredentialsReturnsMaskedProviders(@TempDir Path tempDir) {
        var service = new CredentialsPersistenceService(tempDir.resolve("api-credentials.json"));
        var stored = new ApiCredentials();
        stored.setDefaultProviderId("provider-1");
        stored.setProviders(List.of(
            new ApiCredentials.ApiProvider("provider-1", "Anthropic", "test-secret-9876", "https://api.minimaxi.com/anthropic", List.of(model("MiniMax-M2.7", 200000L)))
        ));
        service.save(stored);
        var controller = new CredentialsController(service);

        var response = controller.getCredentials();

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("provider-1", response.getBody().getDefaultProviderId());
        assertEquals(1, response.getBody().getProviders().size());
        var provider = response.getBody().getProviders().get(0);
        assertEquals("Anthropic", provider.getName());
        assertEquals("****9876", provider.getApiKeyMasked());
        assertTrue(provider.isHasApiKey());
        assertEquals(1, provider.getModels().size());
        assertEquals("MiniMax-M2.7", provider.getModels().get(0).getId());
        assertEquals(200000L, provider.getModels().get(0).getContextWindow());
    }

    @Test
    void updateCredentialsReplacesProviderListAndPreservesExistingApiKeyWhenOmitted(@TempDir Path tempDir) {
        var service = new CredentialsPersistenceService(tempDir.resolve("api-credentials.json"));
        var current = new ApiCredentials();
        current.setDefaultProviderId("provider-1");
        current.setProviders(List.of(
            new ApiCredentials.ApiProvider("provider-1", "Anthropic", "secret-9876", "https://api.minimaxi.com/anthropic", List.of(model("MiniMax-M2.7"))),
            new ApiCredentials.ApiProvider("provider-2", "OpenAI", "openai-secret", "https://api.openai.com/v1", List.of(model("gpt-4o")))
        ));
        service.save(current);
        var controller = new CredentialsController(service);

        var updates = new ApiCredentials();
        updates.setDefaultProviderId("provider-1");
        updates.setProviders(List.of(
            new ApiCredentials.ApiProvider("provider-1", "Anthropic", null, "https://proxy.example/anthropic", List.of(model("MiniMax-M2.5", 64000L)))
        ));

        var response = controller.updateCredentials(updates);
        var reloaded = service.load();

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(1, reloaded.getProviders().size());
        var provider = reloaded.getProviders().get(0);
        assertEquals("provider-1", provider.getId());
        assertEquals("secret-9876", provider.getApiKey());
        assertEquals("https://proxy.example/anthropic", provider.getApiBaseUrl());
        assertEquals(List.of("MiniMax-M2.5"), provider.getModelIds());
        assertEquals(64000L, provider.getModels().get(0).getContextWindow());
    }

    @Test
    void updateCredentialsCanRemoveProviderAndChangeDefault(@TempDir Path tempDir) {
        var service = new CredentialsPersistenceService(tempDir.resolve("api-credentials.json"));
        var current = new ApiCredentials();
        current.setDefaultProviderId("provider-1");
        current.setProviders(List.of(
            new ApiCredentials.ApiProvider("provider-1", "Anthropic", "secret-9876", "https://api.minimaxi.com/anthropic", List.of(model("MiniMax-M2.7"))),
            new ApiCredentials.ApiProvider("provider-2", "OpenAI", "openai-secret", "https://api.openai.com/v1", List.of(model("gpt-4o"), model("gpt-4.1", 256000L)))
        ));
        service.save(current);
        var controller = new CredentialsController(service);

        var updates = new ApiCredentials();
        updates.setDefaultProviderId("provider-2");
        updates.setProviders(List.of(
            new ApiCredentials.ApiProvider("provider-2", "OpenAI", null, "https://api.openai.com/v1", List.of(model("gpt-4o"), model("gpt-4.1", 256000L)))
        ));

        var response = controller.updateCredentials(updates);
        var body = response.getBody();

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(body);
        assertEquals("provider-2", body.getDefaultProviderId());
        assertEquals(1, body.getProviders().size());
        assertEquals("provider-2", body.getProviders().get(0).getId());
        assertFalse(body.getProviders().stream().anyMatch(provider -> "provider-1".equals(provider.getId())));
    }
}
