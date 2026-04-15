package com.coderhino.web.controller;

import com.coderhino.web.dto.ErrorResponse;
import com.coderhino.web.dto.ReferenceDto;
import com.coderhino.web.dto.ReferenceListDto;
import com.coderhino.web.service.ReferenceService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class ReferenceControllerTest {

    @Test
    void listReferencesReturnsReferencePayload() {
        var controller = new ReferenceController(new ReferenceService(null) {
            @Override
            public List<ReferenceDto> listReferences() {
                return List.of(new ReferenceDto("api-guidelines", "Api Guidelines", "# API Guidelines"));
            }
        });

        var response = controller.listReferences();

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        var body = assertInstanceOf(ReferenceListDto.class, response.getBody());
        assertEquals(1, body.references().size());
        assertEquals("api-guidelines", body.references().get(0).id());
    }

    @Test
    void listReferencesReturnsErrorPayloadWhenLoadingFails() {
        var controller = new ReferenceController(new ReferenceService(null) {
            @Override
            public List<ReferenceDto> listReferences() throws IOException {
                throw new IOException("failed");
            }
        });

        var response = controller.listReferences();

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        var body = assertInstanceOf(ErrorResponse.class, response.getBody());
        assertEquals("failed", body.getError());
    }
}
