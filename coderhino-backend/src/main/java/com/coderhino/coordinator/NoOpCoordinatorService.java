package com.coderhino.coordinator;

/**
 * No-op implementation of {@link CoordinatorService}.
 * All operations are non-throwing and deterministic.
 */
public final class NoOpCoordinatorService implements CoordinatorService {

    @Override
    public CoordinatorMode currentMode() {
        return CoordinatorMode.SINGLE;
    }

    @Override
    public void setMode(CoordinatorMode mode) {
        // no-op
    }

    @Override
    public boolean isMultiAgent() {
        return false;
    }

    @Override
    public String serviceName() {
        return "coordinator-service";
    }
}
