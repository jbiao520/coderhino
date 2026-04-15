package com.coderhino.tools.builtin;

import com.coderhino.tools.ToolContext;
import com.coderhino.tools.ToolDefinition;
import com.coderhino.types.PermissionResult;
import com.coderhino.types.ToolInputSchema;
import com.vladsch.flexmark.html2md.converter.FlexmarkHtmlConverter;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class WebFetchTool implements ToolDefinition<WebFetchTool.Input, String> {
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(10);
    private static final int MAX_REDIRECTS = 10;
    private static final int MAX_MARKDOWN_LENGTH = 100_000;
    private static final long CACHE_TTL_MS = 15L * 60 * 1000; // 15 minutes

    private static final FlexmarkHtmlConverter HTML_TO_MD = FlexmarkHtmlConverter.builder().build();

    private record CacheEntry(String content, long timestamp) {}

    private static final Map<String, CacheEntry> URL_CACHE =
        Collections.synchronizedMap(new LinkedHashMap<>(512, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, CacheEntry> eldest) {
                return size() > 512;
            }
        });

    // domain → timestamp (millis) of last ALLOWED result; only ALLOWED is cached
    private static final ConcurrentHashMap<String, Long> DOMAIN_CHECK_CACHE = new ConcurrentHashMap<>();
    private static final long DOMAIN_CHECK_TTL_MS = 5L * 60 * 1000; // 5 minutes

    private final HttpClient httpClient;

    public WebFetchTool() {
        this(HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build());
    }

    public WebFetchTool(HttpClient httpClient) {
        this.httpClient = httpClient;
    }

    @Override
    public String name() {
        return "web_fetch";
    }

    @Override
    public String description() {
        return "Fetch a URL and return text or stripped HTML content";
    }

    @Override
    public boolean isReadOnly() {
        return true;
    }

    @Override
    public ToolInputSchema inputSchema() {
        return ToolInputSchema.object(Map.of(
            "url", Map.of("type", "string"),
            "format", Map.of("type", "string")
        ));
    }

    @Override
    public PermissionResult validate(Input input, ToolContext context) {
        if (input.url() == null || input.url().isBlank()) {
            return PermissionResult.deny("URL must not be blank.");
        }
        try {
            validateURL(input.url());
        } catch (IllegalArgumentException e) {
            return PermissionResult.deny(e.getMessage());
        }
        return PermissionResult.allow();
    }

    @Override
    public String execute(Input input, ToolContext context) throws Exception {
        String url = upgradeToHttps(input.url());
        validateURL(url);

        // Domain preflight blocklist check
        if (!System.getProperty("claudecode.skipWebFetchPreflight", "false").equals("true")
            && !WebFetchPreapproved.isPreapprovedUrl(url)) {
            String hostname = URI.create(url).getHost();
            DomainCheckResult checkResult = checkDomainBlocklist(hostname);
            switch (checkResult) {
                case BLOCKED -> throw new DomainBlockedError(hostname);
                case CHECK_FAILED -> throw new DomainCheckFailedError(hostname);
                case ALLOWED -> {} // continue
            }
        }

        CacheEntry cached = URL_CACHE.get(url);
        if (cached != null && System.currentTimeMillis() - cached.timestamp() < CACHE_TTL_MS) {
            return cached.content();
        }

        String currentUrl = url;
        HttpResponse<String> response = null;
        for (int hop = 0; hop <= MAX_REDIRECTS; hop++) {
            var request = HttpRequest.newBuilder()
                .uri(URI.create(currentUrl))
                .timeout(REQUEST_TIMEOUT)
                .header("User-Agent", "coderhino/1.0")
                .GET()
                .build();
            response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            // Egress proxy detection
            if (response.statusCode() == 403
                && "blocked-by-allowlist".equals(response.headers().firstValue("X-Proxy-Error").orElse(""))) {
                throw new EgressBlockedError(URI.create(currentUrl).getHost());
            }
            int status = response.statusCode();
            if (status == 301 || status == 302 || status == 307 || status == 308) {
                String location = response.headers().firstValue("Location").orElse(null);
                if (location == null) break; // no Location header, treat as final
                // Resolve relative URLs
                String redirectUrl = URI.create(currentUrl).resolve(location).toString();
                if (isPermittedRedirect(currentUrl, redirectUrl)) {
                    currentUrl = redirectUrl;
                    continue;
                } else {
                    return "REDIRECT DETECTED: " + currentUrl + " -> " + redirectUrl;
                }
            }
            break; // non-redirect status
        }

        var body = response.body();

        var format = input.format() == null ? "text" : input.format().toLowerCase();

        if ("html".equals(format)) {
            if (body.length() > MAX_MARKDOWN_LENGTH) {
                body = body.substring(0, MAX_MARKDOWN_LENGTH) + "\n\n[Content truncated due to length...]";
            }
            URL_CACHE.put(url, new CacheEntry(body, System.currentTimeMillis()));
            return body;
        }

        String contentType = response.headers().firstValue("Content-Type").orElse("");
        String result;
        if (contentType.contains("text/html")) {
            result = HTML_TO_MD.convert(body);
        } else {
            result = stripHtml(body).trim();
        }

        if (result.length() > MAX_MARKDOWN_LENGTH) {
            result = result.substring(0, MAX_MARKDOWN_LENGTH) + "\n\n[Content truncated due to length...]";
        }

        URL_CACHE.put(url, new CacheEntry(result, System.currentTimeMillis()));
        return result;
    }

    public static void clearCache() {
        URL_CACHE.clear();
    }

    public static void clearDomainCheckCache() {
        DOMAIN_CHECK_CACHE.clear();
    }

    private static boolean isPermittedRedirect(String originalUrl, String redirectUrl) {
        try {
            URI orig = URI.create(originalUrl);
            URI redir = URI.create(redirectUrl);
            // Same protocol required
            if (!orig.getScheme().equalsIgnoreCase(redir.getScheme())) return false;
            // Same port required (normalize: http default=80, https default=443)
            int origPort = orig.getPort() == -1 ? ("https".equalsIgnoreCase(orig.getScheme()) ? 443 : 80) : orig.getPort();
            int redirPort = redir.getPort() == -1 ? ("https".equalsIgnoreCase(redir.getScheme()) ? 443 : 80) : redir.getPort();
            if (origPort != redirPort) return false;
            // No credentials in redirect
            if (redir.getUserInfo() != null) return false;
            // Strip www. from both hostnames — stripped must be equal
            String origHost = orig.getHost().replaceFirst("^www\\.", "");
            String redirHost = redir.getHost().replaceFirst("^www\\.", "");
            return origHost.equalsIgnoreCase(redirHost);
        } catch (Exception e) { return false; }
    }

    private static void validateURL(String url) {
        if (url.length() > 2000) {
            throw new IllegalArgumentException("URL is too long (max 2000 characters)");
        }
        URI uri = URI.create(url);
        if (uri.getUserInfo() != null) {
            throw new IllegalArgumentException("URLs with credentials are not supported");
        }
        String host = uri.getHost();
        if (host == null || host.split("\\.").length < 2) {
            throw new IllegalArgumentException("URL must have a valid hostname");
        }
        String scheme = uri.getScheme();
        if (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme)) {
            throw new IllegalArgumentException("Only http/https URLs are supported");
        }
    }

    private static String upgradeToHttps(String url) {
        return url.replaceFirst("^http://", "https://");
    }

    private String stripHtml(String html) {
        return html
            .replaceAll("(?is)<script.*?>.*?</script>", " ")
            .replaceAll("(?is)<style.*?>.*?</style>", " ")
            .replaceAll("(?i)<br\\s*/?>", "\n")
            .replaceAll("(?i)</p>", "\n")
            .replaceAll("(?is)<[^>]+>", " ")
            .replaceAll("&nbsp;", " ")
            .replaceAll("&amp;", "&")
            .replaceAll("\\s+", " ");
    }

    private enum DomainCheckResult { ALLOWED, BLOCKED, CHECK_FAILED }

    public static class DomainBlockedError extends RuntimeException {
        DomainBlockedError(String domain) {
            super("Code Rhino is unable to fetch from " + domain);
        }
    }

    public static class DomainCheckFailedError extends RuntimeException {
        DomainCheckFailedError(String domain) {
            super("Unable to verify if domain " + domain + " is safe to fetch. " +
                  "This may be due to network restrictions or enterprise security policies blocking claude.ai.");
        }
    }

    public static class EgressBlockedError extends RuntimeException {
        private final String domain;
        EgressBlockedError(String domain) {
            super("{\"error_type\":\"EGRESS_BLOCKED\",\"domain\":\"" + domain +
                  "\",\"message\":\"Access to " + domain + " is blocked by the network egress proxy.\"}");
            this.domain = domain;
        }
        String getDomain() { return domain; }
    }

    private DomainCheckResult checkDomainBlocklist(String domain) {
        Long cached = DOMAIN_CHECK_CACHE.get(domain);
        if (cached != null && System.currentTimeMillis() - cached < DOMAIN_CHECK_TTL_MS) {
            return DomainCheckResult.ALLOWED;
        }
        try {
            String encodedDomain = URLEncoder.encode(domain, StandardCharsets.UTF_8);
            var request = HttpRequest.newBuilder()
                .uri(URI.create("https://api.anthropic.com/api/web/domain_info?domain=" + encodedDomain))
                .timeout(Duration.ofSeconds(10))
                .GET()
                .build();
            var response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() == 200) {
                String body = response.body();
                if (body.contains("\"can_fetch\":true")) {
                    DOMAIN_CHECK_CACHE.put(domain, System.currentTimeMillis());
                    return DomainCheckResult.ALLOWED;
                }
                return DomainCheckResult.BLOCKED;
            }
            return DomainCheckResult.CHECK_FAILED;
        } catch (Exception e) {
            return DomainCheckResult.CHECK_FAILED;
        }
    }

    public record Input(String url, String format) {
    }
}
