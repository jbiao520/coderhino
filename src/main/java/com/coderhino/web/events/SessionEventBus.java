package com.coderhino.web.events;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

@Service
public class SessionEventBus {

    private static final Logger log = LoggerFactory.getLogger(SessionEventBus.class);

    private final ConcurrentHashMap<String, CopyOnWriteArrayList<SessionEventEmitter>> emittersBySession =
            new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, ReplayBuffer> replayBuffersBySession = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Object> sessionLocks = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper;

    public SessionEventBus(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public SseEmitter register(String sessionId) {
        var eventEmitter = new SessionEventEmitter(sessionId, objectMapper, this::remove);
        synchronized (sessionLock(sessionId)) {
            emittersBySession
                    .computeIfAbsent(sessionId, k -> new CopyOnWriteArrayList<>())
                    .add(eventEmitter);
        }
        log.debug("Registered SSE emitter for session={} emitter={}", sessionId, eventEmitter.getEmitterId());
        return eventEmitter.getSseEmitter();
    }

    public SessionEventEmitter registerWithWrapper(String sessionId) {
        return registerWithReplay(sessionId, null, null);
    }

    public SessionEventEmitter registerWithReplay(String sessionId, String runId, Long afterSequence) {
        var eventEmitter = new SessionEventEmitter(sessionId, objectMapper, this::remove);
        synchronized (sessionLock(sessionId)) {
            emittersBySession
                    .computeIfAbsent(sessionId, k -> new CopyOnWriteArrayList<>())
                    .add(eventEmitter);
            if (runId != null && afterSequence != null) {
                replayBuffersBySession
                    .computeIfAbsent(sessionId, key -> new ReplayBuffer())
                    .eventsAfter(runId, afterSequence)
                    .forEach(eventEmitter::send);
            }
        }
        log.debug("Registered SSE emitter for session={} emitter={}", sessionId, eventEmitter.getEmitterId());
        return eventEmitter;
    }

    private void remove(SessionEventEmitter eventEmitter) {
        var list = emittersBySession.get(eventEmitter.getSessionId());
        if (list != null) {
            list.remove(eventEmitter);
            if (list.isEmpty()) {
                emittersBySession.remove(eventEmitter.getSessionId(), list);
            }
        }
        log.debug("Removed SSE emitter for session={} emitter={}", eventEmitter.getSessionId(), eventEmitter.getEmitterId());
    }

    public void publish(String sessionId, SessionEvent event) {
        publish(sessionId, event, null, null);
    }

    public void publish(String sessionId, SessionEvent event, String runId, Long sequence) {
        synchronized (sessionLock(sessionId)) {
            if (runId != null && sequence != null) {
                replayBuffersBySession
                    .computeIfAbsent(sessionId, key -> new ReplayBuffer())
                    .append(runId, sequence, event);
            }

            var list = emittersBySession.get(sessionId);
            if (list == null || list.isEmpty()) {
                return;
            }
            for (var emitter : list) {
                emitter.send(event);
            }
            log.debug("Published event={} to session={} emitters={}", event.type(), sessionId, list.size());
        }
    }

    public void resetReplay(String sessionId, String runId) {
        synchronized (sessionLock(sessionId)) {
            replayBuffersBySession.put(sessionId, new ReplayBuffer(runId));
        }
    }

    public void clearReplay(String sessionId) {
        synchronized (sessionLock(sessionId)) {
            replayBuffersBySession.remove(sessionId);
        }
    }

    public List<SessionEvent> replayEvents(String sessionId, String runId, long afterSequence) {
        synchronized (sessionLock(sessionId)) {
            var buffer = replayBuffersBySession.get(sessionId);
            if (buffer == null) {
                return List.of();
            }
            return buffer.eventsAfter(runId, afterSequence);
        }
    }

    public int getReplayEventCount(String sessionId) {
        synchronized (sessionLock(sessionId)) {
            var buffer = replayBuffersBySession.get(sessionId);
            return buffer == null ? 0 : buffer.size();
        }
    }

    private Object sessionLock(String sessionId) {
        return sessionLocks.computeIfAbsent(sessionId, key -> new Object());
    }

    private static final class ReplayBuffer {
        private static final int MAX_EVENTS = 256;

        private String runId;
        private final ArrayList<ReplayEvent> events = new ArrayList<>();

        private ReplayBuffer() {
        }

        private ReplayBuffer(String runId) {
            this.runId = runId;
        }

        void append(String runId, long sequence, SessionEvent event) {
            if (this.runId == null || !this.runId.equals(runId)) {
                this.runId = runId;
                events.clear();
            }
            events.add(new ReplayEvent(sequence, event));
            if (events.size() > MAX_EVENTS) {
                events.remove(0);
            }
        }

        List<SessionEvent> eventsAfter(String runId, long afterSequence) {
            if (this.runId == null || !this.runId.equals(runId)) {
                return List.of();
            }
            return events.stream()
                .filter(event -> event.sequence > afterSequence)
                .map(ReplayEvent::event)
                .toList();
        }

        int size() {
            return events.size();
        }

        private record ReplayEvent(long sequence, SessionEvent event) {}
    }

    public int getEmitterCount(String sessionId) {
        var list = emittersBySession.get(sessionId);
        return list == null ? 0 : list.size();
    }

    public int getTotalEmitterCount() {
        return emittersBySession.values().stream().mapToInt(List::size).sum();
    }

    @EventListener(ContextClosedEvent.class)
    public void onContextClosed(ContextClosedEvent event) {
        log.info("Server shutting down — broadcasting server-shutdown to {} session(s)", emittersBySession.size());
        var shutdownEvent = SessionEvent.serverShutdown();
        var allEmitters = new ArrayList<SessionEventEmitter>();
        emittersBySession.values().forEach(allEmitters::addAll);
        for (var emitter : allEmitters) {
            emitter.send(shutdownEvent);
            emitter.complete();
        }
        emittersBySession.clear();
        replayBuffersBySession.clear();
        sessionLocks.clear();
        log.info("SSE shutdown broadcast complete — {} emitter(s) closed", allEmitters.size());
    }
}
