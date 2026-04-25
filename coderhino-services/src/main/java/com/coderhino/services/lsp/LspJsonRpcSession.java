package com.coderhino.services.lsp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;

public final class LspJsonRpcSession {
    private final Process process;
    private final InputStream inputStream;
    private final OutputStream outputStream;
    private final ObjectMapper objectMapper;
    private final AtomicLong requestIds = new AtomicLong(1);
    private final Map<String, List<LspDiagnosticDescriptor>> publishedDiagnostics = new ConcurrentHashMap<>();
    private boolean initialized;

    public LspJsonRpcSession(Process process) {
        this.process = process;
        this.inputStream = process.getInputStream();
        this.outputStream = process.getOutputStream();
        this.objectMapper = new ObjectMapper();
    }

    LspJsonRpcSession(InputStream inputStream, OutputStream outputStream) {
        this.process = null;
        this.inputStream = inputStream;
        this.outputStream = outputStream;
        this.objectMapper = new ObjectMapper();
    }

    public synchronized void initializeIfNeeded() throws Exception {
        if (initialized) {
            return;
        }

        var params = objectMapper.createObjectNode();
        params.put("processId", ProcessHandle.current().pid());
        params.putNull("rootUri");
        params.set("capabilities", objectMapper.createObjectNode());
        params.put("trace", "off");
        var clientInfo = objectMapper.createObjectNode();
        clientInfo.put("name", "coderhino");
        clientInfo.put("version", "1.0.0-SNAPSHOT");
        params.set("clientInfo", clientInfo);

        sendRequest("initialize", params);
        sendNotification("initialized", objectMapper.createObjectNode());
        initialized = true;
    }

    public synchronized List<LspSymbolDescriptor> workspaceSymbols(String query) throws Exception {
        initializeIfNeeded();
        var params = objectMapper.createObjectNode();
        params.put("query", query);
        var response = sendRequest("workspace/symbol", params);
        return parseSymbols(response.path("result"));
    }

    public synchronized List<LspSymbolDescriptor> documentSymbols(String uri) throws Exception {
        initializeIfNeeded();
        var params = objectMapper.createObjectNode();
        var textDocument = objectMapper.createObjectNode();
        textDocument.put("uri", uri);
        params.set("textDocument", textDocument);
        var response = sendRequest("textDocument/documentSymbol", params);
        return parseSymbols(response.path("result"));
    }

    public synchronized List<LspLocationDescriptor> definition(String uri, int line, int character) throws Exception {
        initializeIfNeeded();
        var params = objectMapper.createObjectNode();
        var textDocument = objectMapper.createObjectNode();
        textDocument.put("uri", uri);
        params.set("textDocument", textDocument);
        var position = objectMapper.createObjectNode();
        position.put("line", line);
        position.put("character", character);
        params.set("position", position);
        var response = sendRequest("textDocument/definition", params);
        return parseLocations(response.path("result"));
    }

    public synchronized List<LspLocationDescriptor> references(String uri, int line, int character, boolean includeDeclaration) throws Exception {
        initializeIfNeeded();
        var params = objectMapper.createObjectNode();
        var textDocument = objectMapper.createObjectNode();
        textDocument.put("uri", uri);
        params.set("textDocument", textDocument);
        var position = objectMapper.createObjectNode();
        position.put("line", line);
        position.put("character", character);
        params.set("position", position);
        var context = objectMapper.createObjectNode();
        context.put("includeDeclaration", includeDeclaration);
        params.set("context", context);
        var response = sendRequest("textDocument/references", params);
        return parseLocations(response.path("result"));
    }

    public synchronized List<LspDiagnosticDescriptor> getDiagnostics(String uri) throws Exception {
        initializeIfNeeded();
        var params = objectMapper.createObjectNode();
        var textDocument = objectMapper.createObjectNode();
        textDocument.put("uri", uri);
        params.set("textDocument", textDocument);
        var identifier = objectMapper.createObjectNode();
        identifier.put("value", "claudecode-java");
        params.set("identifier", identifier);
        params.put("previousResultId", "");
        var response = sendRequest("textDocument/diagnostic", params);
        if (response.has("error")) {
            return List.copyOf(publishedDiagnostics.getOrDefault(uri, List.of()));
        }
        var result = response.path("result");
        var items = result.path("items");
        if (items.isArray()) {
            return parseDiagnostics(uri, items);
        }
        return List.copyOf(publishedDiagnostics.getOrDefault(uri, List.of()));
    }

    public List<LspDiagnosticDescriptor> getPublishedDiagnostics(String uri) {
        return List.copyOf(publishedDiagnostics.getOrDefault(uri, List.of()));
    }

    public void handleNotification(JsonNode notification) {
        var method = notification.path("method").asText();
        if ("textDocument/publishDiagnostics".equals(method)) {
            var params = notification.path("params");
            var uri = params.path("uri").asText();
            var diags = params.path("diagnostics");
            if (diags.isArray()) {
                publishedDiagnostics.put(uri, parseDiagnostics(uri, diags));
            }
        }
    }

    public synchronized String hover(String uri, int line, int character) throws Exception {
        initializeIfNeeded();
        var params = objectMapper.createObjectNode();
        var textDocument = objectMapper.createObjectNode();
        textDocument.put("uri", uri);
        params.set("textDocument", textDocument);
        var position = objectMapper.createObjectNode();
        position.put("line", line);
        position.put("character", character);
        params.set("position", position);
        var response = sendRequest("textDocument/hover", params);
        var contents = response.path("result").path("contents");
        if (contents.isTextual()) {
            return contents.asText();
        }
        if (contents.isArray()) {
            var parts = new ArrayList<String>();
            for (JsonNode item : contents) {
                if (item.isTextual()) {
                    parts.add(item.asText());
                } else if (item.has("value")) {
                    parts.add(item.path("value").asText());
                } else {
                    parts.add(item.toString());
                }
            }
            return String.join(System.lineSeparator(), parts);
        }
        if (contents.has("value")) {
            return contents.path("value").asText();
        }
        return response.path("result").toString();
    }

    private List<LspDiagnosticDescriptor> parseDiagnostics(String uri, JsonNode items) {
        var result = new ArrayList<LspDiagnosticDescriptor>();
        for (JsonNode item : items) {
            var range = item.path("range");
            var start = range.path("start");
            result.add(new LspDiagnosticDescriptor(
                uri,
                item.path("message").asText(""),
                item.path("severity").asInt(1),
                item.path("code").asText(""),
                item.path("source").asText(""),
                start.path("line").asInt(0),
                start.path("character").asInt(0)
            ));
        }
        return result;
    }

    private List<LspSymbolDescriptor> parseSymbols(JsonNode result) {
        var symbols = new ArrayList<LspSymbolDescriptor>();
        if (!result.isArray()) {
            return symbols;
        }
        for (JsonNode symbolNode : result) {
            var location = symbolNode.has("location") ? symbolNode.path("location") : symbolNode;
            var range = location.has("range") ? location.path("range") : symbolNode.path("range");
            var start = range.path("start");
            symbols.add(new LspSymbolDescriptor(
                symbolNode.path("name").asText(),
                symbolNode.path("kind").asInt(0),
                location.path("uri").asText(""),
                start.path("line").asInt(0),
                start.path("character").asInt(0)
            ));
        }
        return symbols;
    }

    private List<LspLocationDescriptor> parseLocations(JsonNode result) {
        var locations = new ArrayList<LspLocationDescriptor>();
        if (result.isObject()) {
            addLocation(locations, result);
            return locations;
        }
        if (!result.isArray()) {
            return locations;
        }
        for (JsonNode item : result) {
            addLocation(locations, item);
        }
        return locations;
    }

    private void addLocation(List<LspLocationDescriptor> locations, JsonNode locationNode) {
        JsonNode target;
        if (locationNode.has("targetUri") || locationNode.has("uri")) {
            target = locationNode;
        } else if (locationNode.has("location")) {
            target = locationNode.path("location");
        } else {
            target = locationNode;
        }
        var uri = target.has("targetUri") ? target.path("targetUri").asText("") : target.path("uri").asText("");
        var range = target.has("targetSelectionRange") ? target.path("targetSelectionRange") : target.path("range");
        var start = range.path("start");
        locations.add(new LspLocationDescriptor(
            uri,
            start.path("line").asInt(0),
            start.path("character").asInt(0)
        ));
    }

    public void close() {
        if (process != null && process.isAlive()) {
            process.destroy();
        }
    }

    private JsonNode sendRequest(String method, JsonNode params) throws Exception {
        long id = requestIds.getAndIncrement();
        var payload = objectMapper.createObjectNode();
        payload.put("jsonrpc", "2.0");
        payload.put("id", id);
        payload.put("method", method);
        payload.set("params", params);

        writeMessage(payload);
        while (true) {
            var response = readMessage();
            if (response.has("id") && response.path("id").asLong() == id) {
                return response;
            }
            if (!response.has("id") && response.has("method")) {
                handleNotification(response);
            }
        }
    }

    private void sendNotification(String method, JsonNode params) throws Exception {
        var payload = objectMapper.createObjectNode();
        payload.put("jsonrpc", "2.0");
        payload.put("method", method);
        payload.set("params", params);
        writeMessage(payload);
    }

    private void writeMessage(ObjectNode payload) throws Exception {
        var body = objectMapper.writeValueAsBytes(payload);
        var header = "Content-Length: " + body.length + "\r\n\r\n";
        outputStream.write(header.getBytes(StandardCharsets.UTF_8));
        outputStream.write(body);
        outputStream.flush();
    }

    private JsonNode readMessage() throws Exception {
        int contentLength = -1;
        String line;
        while (!(line = readAsciiLine(inputStream)).isEmpty()) {
            var lower = line.toLowerCase();
            if (lower.startsWith("content-length:")) {
                contentLength = Integer.parseInt(line.substring(line.indexOf(':') + 1).trim());
            }
        }

        if (contentLength < 0) {
            throw new IOException("Missing Content-Length header");
        }

        var body = inputStream.readNBytes(contentLength);
        if (body.length != contentLength) {
            throw new IOException("Unexpected EOF while reading LSP response");
        }
        return objectMapper.readTree(body);
    }

    private String readAsciiLine(InputStream stream) throws IOException {
        var buffer = new ByteArrayOutputStream();
        while (true) {
            int value = stream.read();
            if (value == -1) {
                if (buffer.size() == 0) {
                    throw new IOException("EOF while reading LSP headers");
                }
                break;
            }
            if (value == '\n') {
                break;
            }
            if (value != '\r') {
                buffer.write(value);
            }
        }
        return buffer.toString(StandardCharsets.UTF_8);
    }
}
