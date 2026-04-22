package com.coderhino.server;

import java.util.Optional;

/**
 * Contract for server-mode lifecycle management.
 * Covers startup, shutdown, and mode queries for headless/daemon/API modes.
 */
public interface ServerService {

    /** Start the server in the given mode. Returns a handle or identifier. */
    String start(ServerMode mode, int port);

    /** Request graceful shutdown. */
    void stop();

    /** Whether the server is currently running. */
    boolean isRunning();

    /** Current operating mode, if running. */
    Optional<ServerMode> currentMode();

    /** Service name for diagnostics. */
    String serviceName();
}
