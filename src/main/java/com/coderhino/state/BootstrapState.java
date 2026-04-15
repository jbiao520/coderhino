package com.coderhino.state;

import com.coderhino.types.Message;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;

public final class BootstrapState {
    private final AtomicReference<AppState> state;
    private final CopyOnWriteArrayList<Consumer<AppState>> listeners = new CopyOnWriteArrayList<>();

    public BootstrapState(AppState initialState) {
        this.state = new AtomicReference<>(Objects.requireNonNull(initialState));
    }

    public AppState get() {
        return state.get();
    }

    public AppState update(UnaryOperator<AppState> updater) {
        var previous = state.get();
        var next = state.updateAndGet(current -> Objects.requireNonNull(updater.apply(current)));
        if (next != previous) {
            notifyListeners(next);
        }
        return next;
    }

    public void addMessage(Message message) {
        update(current -> current.addMessage(message));
    }

    public void clearMessages() {
        update(AppState::clearMessages);
    }

    public void stop() {
        update(AppState::stop);
    }

    public Runnable onChange(Consumer<AppState> listener) {
        listeners.add(Objects.requireNonNull(listener));
        return () -> listeners.remove(listener);
    }

    public List<Consumer<AppState>> listeners() {
        return List.copyOf(listeners);
    }

    private void notifyListeners(AppState newState) {
        for (var listener : listeners) {
            listener.accept(newState);
        }
    }
}
