package com.coderhino.web.controller;

import com.coderhino.web.settings.SettingsPersistenceService;
import com.coderhino.web.settings.WebSettings;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.HttpStatus;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class SettingsControllerTest {

    @Test
    void getSettingsReturnsFontDefaultsWhenNotPersisted(@TempDir Path tempDir) {
        var service = new SettingsPersistenceService(tempDir.resolve("web-settings.json"));
        var controller = new SettingsController(service);

        var response = controller.getSettings();

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("sans", response.getBody().getSidebarFontFamily());
        assertEquals(13, response.getBody().getSidebarFontSize());
        assertEquals("sans", response.getBody().getChatFontFamily());
        assertEquals(13, response.getBody().getChatFontSize());
        assertEquals(List.of(), response.getBody().getReferenceSourcePaths());
    }

    @Test
    void updateSettingsPersistsFontFieldsAndReturnsResolvedValues(@TempDir Path tempDir) {
        var service = new SettingsPersistenceService(tempDir.resolve("web-settings.json"));
        var controller = new SettingsController(service);

        var updates = new WebSettings();
        updates.setSidebarFontFamily("mono");
        updates.setSidebarFontSize(16);
        updates.setChatFontFamily("sans");
        updates.setChatFontSize(15);
        updates.setReferenceSourcePaths(List.of("/tmp/docs", "/tmp/notes"));

        var response = controller.updateSettings(updates);
        var stored = service.load();

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("mono", response.getBody().getSidebarFontFamily());
        assertEquals(16, response.getBody().getSidebarFontSize());
        assertEquals("sans", response.getBody().getChatFontFamily());
        assertEquals(15, response.getBody().getChatFontSize());
        assertEquals("mono", stored.getSidebarFontFamily());
        assertEquals(16, stored.getSidebarFontSize());
        assertEquals("sans", stored.getChatFontFamily());
        assertEquals(15, stored.getChatFontSize());
        assertEquals(List.of("/tmp/docs", "/tmp/notes"), response.getBody().getReferenceSourcePaths());
        assertEquals(List.of("/tmp/docs", "/tmp/notes"), stored.getReferenceSourcePaths());
    }
}
