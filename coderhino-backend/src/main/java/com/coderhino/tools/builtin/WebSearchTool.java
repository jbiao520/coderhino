package com.coderhino.tools.builtin;

import com.coderhino.tools.ToolContext;
import com.coderhino.tools.ToolDefinition;
import com.coderhino.types.PermissionResult;
import com.coderhino.types.ToolInputSchema;

import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

public final class WebSearchTool implements ToolDefinition<WebSearchTool.Input, List<String>> {
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(10);
    private static final int MAX_RESULTS = 20;
    private static final int MAX_RESPONSE_SIZE = 500 * 1024;
    private static final Pattern LINK_PATTERN = Pattern.compile("<a[^>]+href=\"([^\"]+)\"[^>]*>(.*?)</a>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

    private final HttpClient httpClient;

    public WebSearchTool() {
        this(HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).build());
    }

    public WebSearchTool(HttpClient httpClient) {
        this.httpClient = httpClient;
    }

    @Override
    public String name() {
        return "web_search";
    }

    @Override
    public String description() {
        return "Search the web and return top result links";
    }

    @Override
    public boolean isReadOnly() {
        return true;
    }

    @Override
    public ToolInputSchema inputSchema() {
        return ToolInputSchema.object(Map.of(
            "query", Map.of("type", "string"),
            "limit", Map.of("type", "integer")
        ));
    }

    @Override
    public PermissionResult validate(Input input, ToolContext context) {
        if (input.query() == null || input.query().isBlank()) {
            return PermissionResult.deny("Query must not be blank.");
        }
        return PermissionResult.allow();
    }

    @Override
    public List<String> execute(Input input, ToolContext context) throws Exception {
        int requestedLimit = input.limit() == null ? 5 : Math.max(1, input.limit());
        int limit = Math.min(requestedLimit, MAX_RESULTS);

        var request = HttpRequest.newBuilder()
            .uri(java.net.URI.create("https://duckduckgo.com/html/?q=" + URLEncoder.encode(input.query(), StandardCharsets.UTF_8)))
            .timeout(REQUEST_TIMEOUT)
            .header("User-Agent", "coderhino/1.0")
            .GET()
            .build();

        var response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        var body = response.body();

        if (body.length() > MAX_RESPONSE_SIZE) {
            body = body.substring(0, MAX_RESPONSE_SIZE);
        }

        var matcher = LINK_PATTERN.matcher(body);
        var results = new ArrayList<String>();
        while (matcher.find() && results.size() < limit) {
            var href = matcher.group(1).replace("&amp;", "&");
            var text = matcher.group(2).replaceAll("<[^>]+>", " ").replaceAll("\\s+", " ").trim();
            if (!href.startsWith("http")) {
                continue;
            }
            results.add(text.isBlank() ? href : text + " -> " + href);
        }
        return results;
    }

    public record Input(String query, Integer limit) {
    }
}
