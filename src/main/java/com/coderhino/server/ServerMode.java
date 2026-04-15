package com.coderhino.server;

/**
 * Operating modes for the server subsystem.
 * Maps to TypeScript server/ feature flags (DAEMON, API, etc.).
 */
public enum ServerMode {
    /** No UI — headless batch execution. */
    HEADLESS,
    /** Long-running background daemon. */
    DAEMON,
    /** HTTP API server for external integrations. */
    API
}
