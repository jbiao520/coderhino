package com.coderhino.web.controller;

import com.coderhino.web.settings.SettingsPersistenceService;
import com.coderhino.web.settings.WebSettings;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/settings")
public class SettingsController {

    private final SettingsPersistenceService persistenceService;

    public SettingsController(SettingsPersistenceService persistenceService) {
        this.persistenceService = persistenceService;
    }

    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<WebSettings> getSettings() {
        return ResponseEntity.ok(withDefaults(persistenceService.load()));
    }

    @PutMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<WebSettings> updateSettings(@RequestBody WebSettings updates) {
        var current = persistenceService.load();
        merge(current, updates);
        persistenceService.save(current);
        return ResponseEntity.ok(withDefaults(current));
    }

    private WebSettings withDefaults(WebSettings settings) {
        var resolved = settings.copy();
        if (resolved.getSidebarFontFamily() == null || resolved.getSidebarFontFamily().isBlank()) {
            resolved.setSidebarFontFamily("sans");
        }
        if (resolved.getSidebarFontSize() == null) {
            resolved.setSidebarFontSize(13);
        }
        if (resolved.getChatFontFamily() == null || resolved.getChatFontFamily().isBlank()) {
            resolved.setChatFontFamily("sans");
        }
        if (resolved.getChatFontSize() == null) {
            resolved.setChatFontSize(13);
        }
        if (resolved.getReferenceSourcePaths() == null) {
            resolved.setReferenceSourcePaths(List.of());
        }
        return resolved;
    }

    private void merge(WebSettings target, WebSettings source) {
        if (source.getDefaultPermissionMode() != null) {
            target.setDefaultPermissionMode(source.getDefaultPermissionMode());
        }
        if (source.getTheme() != null) {
            target.setTheme(source.getTheme());
        }
        if (source.getDefaultModel() != null) {
            target.setDefaultModel(source.getDefaultModel());
        }
        if (source.getSidebarFontFamily() != null) {
            target.setSidebarFontFamily(source.getSidebarFontFamily());
        }
        if (source.getSidebarFontSize() != null) {
            target.setSidebarFontSize(source.getSidebarFontSize());
        }
        if (source.getChatFontFamily() != null) {
            target.setChatFontFamily(source.getChatFontFamily());
        }
        if (source.getChatFontSize() != null) {
            target.setChatFontSize(source.getChatFontSize());
        }
        if (source.getReferenceSourcePaths() != null) {
            target.setReferenceSourcePaths(source.getReferenceSourcePaths());
        }
    }
}
