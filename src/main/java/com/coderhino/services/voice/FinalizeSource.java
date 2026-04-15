package com.coderhino.services.voice;

/**
 * Describes how a voice stream was finalized.
 * Maps to the TypeScript {@code FinalizeSource} union type in voiceStreamSTT.ts.
 */
public enum FinalizeSource {

    /** The CloseStream control message was sent and acknowledged by the server endpoint. */
    POST_CLOSESTREAM_ENDPOINT,

    /** No transcript data arrived within the no-data timeout after CloseStream. */
    NO_DATA_TIMEOUT,

    /** The last-resort safety timeout fired before the WebSocket tore down. */
    SAFETY_TIMEOUT,

    /** The WebSocket closed normally. */
    WS_CLOSE,

    /** The WebSocket was already closed when finalize was invoked. */
    WS_ALREADY_CLOSED
}
