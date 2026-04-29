package com.coderhino.verification.spring.chat;

public record ChatResponse(
    String finalText,
    String stopReason,
    int iterationCount,
    boolean success
) {
}
