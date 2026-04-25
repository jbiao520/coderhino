package com.coderhino.services.trigger;

import java.util.Map;
import java.util.function.Consumer;

public interface RemoteTriggerService {

    void registerHandler(String eventType, Consumer<Map<String, Object>> handler);

    void dispatch(String eventType, Map<String, Object> payload);

    boolean isRegistered(String eventType);

    void unregisterHandler(String eventType);

    default String serviceName() {
        return "remote-trigger-service";
    }
}