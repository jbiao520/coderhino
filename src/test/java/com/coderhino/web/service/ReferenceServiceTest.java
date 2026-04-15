package com.coderhino.web.service;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.ResourcePatternResolver;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ReferenceServiceTest {

    @Test
    void listReferencesLoadsMarkdownAssetsAndIgnoresNonMarkdownFiles() throws Exception {
        var service = new ReferenceService(new StubResolver(
            namedResource("bug-investigation.md", "# Bug Investigation\n"),
            namedResource("api-guidelines.md", "# API Guidelines\n"),
            namedResource("notes.txt", "ignore")
        ));

        var references = service.listReferences();

        assertEquals(2, references.size());
        assertEquals("api-guidelines", references.get(0).id());
        assertEquals("Api Guidelines", references.get(0).label());
        assertEquals("# API Guidelines\n", references.get(0).markdown());
        assertEquals("bug-investigation", references.get(1).id());
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
