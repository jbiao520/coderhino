package com.coderhino.services.trigger;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

public final class DefaultRemoteTriggerService implements RemoteTriggerService {

    private final ConcurrentHashMap<String, Consumer<Map<String, Object>>> handlers;

    public DefaultRemoteTriggerService() {
        this.handlers = new ConcurrentHashMap<>();
    }

    @Override
    public void registerHandler(String eventType, Consumer<Map<String, Object>> handler) {
        if (eventType == null || eventType.isBlank()) {
            throw new IllegalArgumentException("eventType must not be blank");
        }
        if (handler == null) {
            throw new IllegalArgumentException("handler must not be null");
        }
        handlers.put(eventType, handler);
    }

    @Override
    public void dispatch(String eventType, Map<String, Object> payload) {
        if (eventType == null || eventType.isBlank()) {
            return;
        }
        Consumer<Map<String, Object>> handler = handlers.get(eventType);
        if (handler != null) {
            try {
                handler.accept(payload != null ? payload : Map.of());
            } catch (Exception ignored) {
            }
        }
    }

    @Override
    public boolean isRegistered(String eventType) {
        if (eventType == null || eventType.isBlank()) {
            return false;
        }
        return handlers.containsKey(eventType);
    }

    @Override
    public void unregisterHandler(String eventType) {
        if (eventType != null) {
            handlers.remove(eventType);
        }
    }
}
