package com.coderhino.query;

final class NoOpModelStreamEventSink implements ModelStreamEventSink {

    static final NoOpModelStreamEventSink INSTANCE = new NoOpModelStreamEventSink();

    private NoOpModelStreamEventSink() {
    }

    @Override
    public void onTextDelta(String text) {
    }

    @Override
    public void onStatus(String message) {
    }
}
