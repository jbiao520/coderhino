package com.coderhino.server;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.web.server.PortInUseException;
import org.springframework.context.ConfigurableApplicationContext;

import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

public final class LocalServerService implements ServerService {

    private static final String WEB_APPLICATION_CLASS = "com.coderhino.web.CodeRhinoWebApplication";

    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicReference<ServerMode> currentMode = new AtomicReference<>();
    private final AtomicReference<ExecutorService> serverExecutor = new AtomicReference<>();
    private final AtomicReference<ConfigurableApplicationContext> springContext = new AtomicReference<>();
    private final AtomicReference<Throwable> startupError = new AtomicReference<>();

    @Override
    public String start(ServerMode mode, int port) {
        if (running.get()) {
            throw new IllegalStateException("Server is already running in mode: " + currentMode.get());
        }

        currentMode.set(mode);
        running.set(true);
        startupError.set(null);

        if (mode == ServerMode.API) {
            startSpringBoot(port);
        } else {
            startStubLoop(mode);
        }

        return mode.name().toLowerCase() + ":" + port;
    }

    private void startSpringBoot(int port) {
        CountDownLatch ready = new CountDownLatch(1);

        ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "local-server-api");
            t.setDaemon(true);
            return t;
        });
        serverExecutor.set(executor);

        executor.submit(() -> {
            try {
                SpringApplication app = new SpringApplication(resolveWebApplicationClass());
                app.addListeners((ApplicationReadyEvent event) -> {
                    springContext.set(event.getApplicationContext());
                    ready.countDown();
                });
                ConfigurableApplicationContext ctx = app.run(
                    "--server.port=" + port,
                    "--server.address=127.0.0.1"
                );
                springContext.compareAndSet(null, ctx);
            } catch (Exception e) {
                Throwable cause = e;
                while (cause != null && !(cause instanceof PortInUseException)) {
                    cause = cause.getCause();
                }
                if (cause instanceof PortInUseException portEx) {
                    startupError.set(portEx);
                    System.err.println("Error: Port " + port + " is already in use. Stop the process occupying the port and retry.");
                } else {
                    startupError.set(e);
                }
                running.set(false);
                ready.countDown();
            }
        });

        try {
            boolean started = ready.await(30, TimeUnit.SECONDS);
            Throwable error = startupError.get();
            if (error instanceof PortInUseException portEx) {
                throw portEx;
            }
            if (!started || !running.get()) {
                if (error != null) {
                    throw new IllegalStateException("Spring Boot failed to start: " + error.getMessage(), error);
                }
                throw new IllegalStateException("Spring Boot failed to start within 30 seconds");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting for Spring Boot to start", e);
        }
    }

    private Class<?> resolveWebApplicationClass() {
        try {
            return Class.forName(WEB_APPLICATION_CLASS);
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException(
                "Web runtime module is not available on the classpath: " + WEB_APPLICATION_CLASS,
                e
            );
        }
    }

    private void startStubLoop(ServerMode mode) {
        ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "local-server-" + mode.name().toLowerCase());
            t.setDaemon(true);
            return t;
        });
        serverExecutor.set(executor);

        executor.submit(() -> {
            while (running.get()) {
                try {
                    Thread.sleep(50);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        });
    }

    @Override
    public void stop() {
        if (!running.get()) {
            return;
        }
        running.set(false);
        currentMode.set(null);

        ConfigurableApplicationContext ctx = springContext.getAndSet(null);
        if (ctx != null && ctx.isRunning()) {
            ctx.close();
        }

        ExecutorService executor = serverExecutor.getAndSet(null);
        if (executor != null) {
            executor.shutdown();
            try {
                if (!executor.awaitTermination(500, TimeUnit.MILLISECONDS)) {
                    executor.shutdownNow();
                }
            } catch (InterruptedException e) {
                executor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }

    @Override
    public boolean isRunning() {
        return running.get();
    }

    @Override
    public Optional<ServerMode> currentMode() {
        return Optional.ofNullable(currentMode.get());
    }

    @Override
    public String serviceName() {
        return "LocalServerService";
    }
}
