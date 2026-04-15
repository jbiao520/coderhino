package com.coderhino.web.controller;

import com.coderhino.web.events.SessionEvent;
import com.coderhino.web.events.SessionEventBus;
import com.coderhino.web.session.WebSessionRegistry;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/sessions")
public class EventController {

    private final WebSessionRegistry sessionRegistry;
    private final SessionEventBus eventBus;

    public EventController(WebSessionRegistry sessionRegistry, SessionEventBus eventBus) {
        this.sessionRegistry = sessionRegistry;
        this.eventBus = eventBus;
    }

    @GetMapping(value = "/{sessionId}/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public ResponseEntity<SseEmitter> getEvents(@PathVariable("sessionId") String sessionId,
                                                @RequestParam(value = "runId", required = false) String runId,
                                                @RequestParam(value = "afterSequence", required = false) Long afterSequence) {
        var sessionOpt = sessionRegistry.find(sessionId);
        if (sessionOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        var session = sessionOpt.get();

        var emitterWrapper = eventBus.registerWithReplay(sessionId, runId, afterSequence);
        emitterWrapper.send(SessionEvent.ready(sessionId, session.getMessageCount()));

        return ResponseEntity.ok()
                .contentType(MediaType.TEXT_EVENT_STREAM)
                .body(emitterWrapper.getSseEmitter());
    }
}
