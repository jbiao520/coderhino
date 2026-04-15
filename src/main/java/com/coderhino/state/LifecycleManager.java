package com.coderhino.state;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Manages startup and shutdown lifecycle hooks for the CLI runtime.
 * Startup hooks execute on {@link #start()}, shutdown hooks execute on {@link #shutdown()}.
 * A JVM shutdown hook is registered so shutdown hooks fire even on SIGTERM/SIGINT.
 */
public final class LifecycleManager {

    private final List<Runnable> startupHooks = new ArrayList<>();
    private final List<Runnable> shutdownHooks = new ArrayList<>();
    private final AtomicBoolean started = new AtomicBoolean(false);
    private final AtomicBoolean shutDown = new AtomicBoolean(false);
    private final Thread jvmShutdownHook;

    public LifecycleManager() {
        this.jvmShutdownHook = new Thread(this::shutdown, "lifecycle-shutdown-hook");
    }

    /**
     * Register a hook to run during {@link #start()}.
     * Must be called before {@link #start()}.
     *
     * @throws IllegalStateException if already started
     */
    public void registerStartupHook(Runnable hook) {
        if (started.get()) {
            throw new IllegalStateException("Cannot register startup hook after start()");
        }
        startupHooks.add(hook);
    }

    /**
     * Register a hook to run during {@link #shutdown()}.
     * May be called at any time before or after start().
     */
    public void registerShutdownHook(Runnable hook) {
        shutdownHooks.add(hook);
    }

    /**
     * Execute all startup hooks in registration order.
     * Also registers a JVM shutdown hook for graceful termination.
     *
     * @throws IllegalStateException if already started
     */
    public void start() {
        if (!started.compareAndSet(false, true)) {
            throw new IllegalStateException("LifecycleManager already started");
        }
        Runtime.getRuntime().addShutdownHook(jvmShutdownHook);
        for (Runnable hook : startupHooks) {
            hook.run();
        }
    }

    /**
     * Execute all shutdown hooks in registration order.
     * Safe to call multiple times; subsequent calls are no-ops.
     */
    public void shutdown() {
        if (!shutDown.compareAndSet(false, true)) {
            return;
        }
        try {
            Runtime.getRuntime().removeShutdownHook(jvmShutdownHook);
        } catch (IllegalStateException ignored) {
        }
        for (Runnable hook : shutdownHooks) {
            try {
                hook.run();
            } catch (Exception e) {
                System.err.println("Lifecycle shutdown hook failed: " + e.getMessage());
            }
        }
    }

    public boolean isStarted() {
        return started.get();
    }

    public boolean isShutDown() {
        return shutDown.get();
    }
}
