package com.coderhino.server;

import java.util.Optional;

/**
 * No-op implementation of {@link ServerService} for default wiring.
 */
public final class NoOpServerService implements ServerService {

    @Override
    public String start(ServerMode mode, int port) {
        return "no-op";
    }

    @Override
    public void stop() {
    }

    @Override
    public boolean isRunning() {
        return false;
    }

    @Override
    public Optional<ServerMode> currentMode() {
        return Optional.empty();
    }

    @Override
    public String serviceName() {
        return "NoOpServerService";
    }
}
