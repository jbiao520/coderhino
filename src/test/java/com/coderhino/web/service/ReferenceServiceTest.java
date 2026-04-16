package com.coderhino.web.service;

import com.coderhino.web.settings.SettingsPersistenceService;
import com.coderhino.web.settings.WebSettings;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.ResourcePatternResolver;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ReferenceServiceTest {

    @Test
    void listReferencesLoadsMarkdownAssetsAndConfiguredDirectoriesAndIgnoresNonMarkdownFiles(@TempDir Path tempDir) throws Exception {
        var referencesDir = Files.createDirectories(tempDir.resolve("references"));
        Files.writeString(referencesDir.resolve("team-notes.md"), "# Team Notes\n");
        Files.writeString(referencesDir.resolve("notes.txt"), "ignore");
        var settingsService = new SettingsPersistenceService(tempDir.resolve("web-settings.json"));
        var settings = new WebSettings();
        settings.setReferenceSourcePaths(List.of(referencesDir.toString()));
        settingsService.save(settings);

        var service = new ReferenceService(new StubResolver(
            namedResource("bug-investigation.md", "# Bug Investigation\n"),
            namedResource("api-guidelines.md", "# API Guidelines\n"),
            namedResource("notes.txt", "ignore")
        ), settingsService);

        var references = service.listReferences();

        assertEquals(3, references.size());
        assertEquals("api-guidelines", references.get(0).id());
        assertEquals("Api Guidelines", references.get(0).label());
        assertEquals("api-guidelines.md", references.get(0).filename());
        assertEquals("# API Guidelines\n", references.get(0).markdown());
        assertEquals("bug-investigation", references.get(1).id());
        assertEquals("team-notes.md", references.get(2).filename());
        assertEquals("references", references.get(2).source());
    }

    @Test
    void listReferencesIgnoresMissingAndDuplicateConfiguredPaths(@TempDir Path tempDir) throws Exception {
        var referencesDir = Files.createDirectories(tempDir.resolve("references"));
        Files.writeString(referencesDir.resolve("api-guidelines.md"), "# Local API Guidelines\n");
        var emptyDir = Files.createDirectories(tempDir.resolve("empty"));

        var settingsService = new SettingsPersistenceService(tempDir.resolve("web-settings.json"));
        var settings = new WebSettings();
        settings.setReferenceSourcePaths(List.of(
            tempDir.resolve("missing").toString(),
            referencesDir.toString(),
            emptyDir.toString(),
            referencesDir.toString()
        ));
        settingsService.save(settings);

        var service = new ReferenceService(new StubResolver(
            namedResource("api-guidelines.md", "# Bundled API Guidelines\n")
        ), settingsService);

        var references = service.listReferences();

        assertEquals(2, references.size());
        assertEquals("api-guidelines", references.get(0).id());
        assertEquals("api-guidelines-2", references.get(1).id());
        assertEquals("api-guidelines.md", references.get(1).filename());
    }

    private static Resource namedResource(String filename, String content) {
        return new ByteArrayResource(content.getBytes()) {
            @Override
            public String getFilename() {
                return filename;
            }
        };
    }

    private record StubResolver(Resource... resources) implements ResourcePatternResolver {
        @Override
        public Resource[] getResources(String locationPattern) {
            return resources;
        }

        @Override
        public Resource getResource(String location) {
            throw new UnsupportedOperationException();
        }

        @Override
        public ClassLoader getClassLoader() {
            return ReferenceServiceTest.class.getClassLoader();
        }
    }
}
