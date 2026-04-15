package com.coderhino.web.events;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SessionEventTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void completedEventIncludesFinalText() throws Exception {
        var json = objectMapper.writeValueAsString(SessionEvent.completed("run-1", "final answer", null, 3L, "proj-1", "ses-1"));

        assertTrue(json.contains("\"type\":\"completed\""));
        assertTrue(json.contains("\"runId\":\"run-1\""));
        assertTrue(json.contains("\"status\":\"COMPLETED\""));
        assertTrue(json.contains("\"finalText\":\"final answer\""));
        assertTrue(json.contains("\"projectId\":\"proj-1\""));
        assertTrue(json.contains("\"sessionId\":\"ses-1\""));
        assertTrue(json.contains("\"sequence\":3"));
    }

    @Test
    void failedEventOmitsFinalTextAndIncludesError() throws Exception {
        var json = objectMapper.writeValueAsString(SessionEvent.failed("run-2", "boom"));

        assertTrue(json.contains("\"type\":\"failed\""));
        assertTrue(json.contains("\"runId\":\"run-2\""));
        assertTrue(json.contains("\"status\":\"FAILED\""));
        assertTrue(json.contains("\"error\":\"boom\""));
        assertFalse(json.contains("finalText"));
    }

    @Test
    void toolEventsUseBackendFieldNames() throws Exception {
        var toolCallJson = objectMapper.writeValueAsString(
                SessionEvent.toolCall("run-3", "glob", "tool-1", "{\"pattern\":\"*.ts\"}"));
        var toolResultJson = objectMapper.writeValueAsString(
                SessionEvent.toolResult("run-3", "glob", "tool-1", "src/index.ts"));
        var textChunkJson = objectMapper.writeValueAsString(SessionEvent.textChunk("run-3", "hello"));

        assertTrue(toolCallJson.contains("\"argumentsJson\":\"{\\\"pattern\\\":\\\"*.ts\\\"}\""));
        assertTrue(toolResultJson.contains("\"result\":\"src/index.ts\""));
        assertTrue(textChunkJson.contains("\"chunk\":\"hello\""));
    }

    @Test
    void richerModelProgressEventsUseDedicatedPayloadFields() throws Exception {
        var thinkingJson = objectMapper.writeValueAsString(SessionEvent.thinkingDelta("run-4", "plan", 7L));
        var toolInputJson = objectMapper.writeValueAsString(SessionEvent.toolInputDelta("run-4", "glob", "tool-2", "{\"pattern\":", 8L));

        assertTrue(thinkingJson.contains("\"type\":\"thinking_delta\""));
        assertTrue(thinkingJson.contains("\"thinking\":\"plan\""));
        assertTrue(thinkingJson.contains("\"sequence\":7"));
        assertTrue(toolInputJson.contains("\"type\":\"tool_input_delta\""));
        assertTrue(toolInputJson.contains("\"toolName\":\"glob\""));
        assertTrue(toolInputJson.contains("\"toolUseId\":\"tool-2\""));
        assertTrue(toolInputJson.contains("\"partialJson\":\"{\\\"pattern\\\":"));
        assertTrue(toolInputJson.contains("\"sequence\":8"));
    }
}
