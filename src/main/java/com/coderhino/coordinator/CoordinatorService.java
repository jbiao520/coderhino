package com.coderhino.coordinator;

import java.util.List;
import java.util.Optional;

/**
 * Service interface for coordinator orchestration mode management.
 */
public interface CoordinatorService {

    CoordinatorMode currentMode();

    void setMode(CoordinatorMode mode);

    boolean isMultiAgent();

    default String serviceName() {
        return "coordinator-service";
    }

    default Optional<String> matchSessionMode(CoordinatorMode requestedMode) {
        return Optional.empty();
    }

    default List<String> getWorkerToolsContext() {
        return List.of();
    }

    default String getCoordinatorSystemPrompt(String workerContext) {
        return "";
    }

    default boolean isCoordinatorModeAvailable() {
        return false;
    }
}
