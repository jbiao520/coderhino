package com.coderhino.web.events;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.UUID;
import java.util.function.Consumer;

public final class SessionEventEmitter {

    private static final Logger log = LoggerFactory.getLogger(SessionEventEmitter.class);

    private final String emitterId = UUID.randomUUID().toString();
    private final String sessionId;
    private final SseEmitter emitter;
    private final ObjectMapper objectMapper;
    private volatile boolean active = true;

    public SessionEventEmitter(String sessionId, ObjectMapper objectMapper, Consumer<SessionEventEmitter> onRemove) {
        this.sessionId = sessionId;
        this.objectMapper = objectMapper;
        this.emitter = new SseEmitter(0L);

        this.emitter.onCompletion(() -> {
            active = false;
            onRemove.accept(this);
            log.debug("SSE emitter completed: session={} emitter={}", sessionId, emitterId);
        });

        this.emitter.onTimeout(() -> {
            active = false;
            onRemove.accept(this);
            this.emitter.complete();
            log.debug("SSE emitter timed out: session={} emitter={}", sessionId, emitterId);
        });

        this.emitter.onError(ex -> {
            active = false;
            onRemove.accept(this);
            log.debug("SSE emitter error: session={} emitter={} error={}", sessionId, emitterId, ex.getMessage());
        });
    }

    public SseEmitter getSseEmitter() {
        return emitter;
    }

    public String getEmitterId() {
        return emitterId;
    }

    public String getSessionId() {
        return sessionId;
    }

    public boolean isActive() {
        return active;
    }

    public void send(SessionEvent event) {
        if (!active) {
            return;
        }
        try {
            var eventName = event.type().name().replace('_', '-');
            var data = objectMapper.writeValueAsString(event.payload());
            var sseEvent = SseEmitter.event()
                    .name(eventName)
                    .data(data)
                    .build();
            emitter.send(sseEvent);
        } catch (IOException e) {
            log.debug("Failed to send SSE event to session={} emitter={}: {}", sessionId, emitterId, e.getMessage());
            active = false;
            emitter.completeWithError(e);
        }
    }

    public void sendKeepAlive() {
        if (!active) {
            return;
        }
        try {
            emitter.send(SseEmitter.event().comment("keep-alive").build());
        } catch (IOException e) {
            log.debug("Failed to send keep-alive to session={} emitter={}", sessionId, emitterId);
            active = false;
            emitter.completeWithError(e);
        }
    }

    public void complete() {
        if (active) {
            active = false;
            emitter.complete();
        }
    }
}
