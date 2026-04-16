package com.coderhino.web.controller;

import com.coderhino.web.credentials.ApiCredentials;
import com.coderhino.web.credentials.CredentialsPersistenceService;
import com.coderhino.web.dto.CredentialsDto;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.LinkedHashMap;

@RestController
@RequestMapping("/api/credentials")
public class CredentialsController {

    private final CredentialsPersistenceService persistenceService;

    public CredentialsController(CredentialsPersistenceService persistenceService) {
        this.persistenceService = persistenceService;
    }

    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<CredentialsDto> getCredentials() {
        var credentials = persistenceService.load();
        return ResponseEntity.ok(toDto(credentials));
    }

    @PutMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<CredentialsDto> updateCredentials(@RequestBody ApiCredentials updates) {
        var current = persistenceService.load();
        merge(current, updates == null ? new ApiCredentials() : updates);
        persistenceService.save(current);
        return ResponseEntity.ok(toDto(current));
    }

    private void merge(ApiCredentials target, ApiCredentials source) {
        var existingById = new LinkedHashMap<String, ApiCredentials.ApiProvider>();
        for (var provider : target.getProviders()) {
            if (provider != null && provider.getId() != null) {
                existingById.put(provider.getId(), provider);
            }
        }

        var mergedProviders = new ArrayList<ApiCredentials.ApiProvider>();
        for (var update : source.getProviders()) {
            if (update == null) {
                continue;
            }
            var existing = existingById.get(update.getId());
            var merged = new ApiCredentials.ApiProvider();
            merged.setId(update.getId());
            merged.setName(update.getName());
            merged.setApiBaseUrl(update.getApiBaseUrl());
            merged.setModels(update.getModels());
            merged.setApiType(update.getApiType());
            if (update.getApiKey() != null) {
                merged.setApiKey(update.getApiKey());
            } else if (existing != null) {
                merged.setApiKey(existing.getApiKey());
            }
            mergedProviders.add(merged);
        }
        target.setProviders(mergedProviders);
        target.setDefaultProviderId(source.getDefaultProviderId());
    }

    private CredentialsDto toDto(ApiCredentials credentials) {
        var providers = credentials.getProviders().stream()
            .map(provider -> {
                var apiKey = provider.getApiKey();
                var hasApiKey = apiKey != null && !apiKey.isEmpty();
                var models = provider.getModels().stream()
                    .map(model -> new CredentialsDto.ProviderDto.ModelDto(model.getId(), model.getContextWindow()))
                    .toList();
                return new CredentialsDto.ProviderDto(
                    provider.getId(),
                    provider.getName(),
                    persistenceService.maskApiKey(apiKey),
                    provider.getApiBaseUrl(),
                    models,
                    provider.getApiType(),
                    hasApiKey
                );
            })
            .toList();
        return new CredentialsDto(credentials.getDefaultProviderId(), providers);
    }
}
