package io.truthcrawl.cli;

import io.truthcrawl.core.MerkleTree;
import io.truthcrawl.core.ObservationRecord;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Minimal HTTP fetcher that produces an ObservationRecord.
 *
 * <p>Uses raw body bytes for content_hash (no normalization pipeline in M2).
 * Follows redirects. Captures selected headers and basic directives.
 */
final class HttpObserver {

    private static final Duration TIMEOUT = Duration.ofSeconds(30);
    private static final Set<String> CAPTURED_HEADERS = Set.of(
            "content-type", "server", "x-robots-tag", "last-modified", "etag");

    private HttpObserver() {}

    /**
     * Fetch a URL and build an unsigned ObservationRecord.
     *
     * @param targetUrl    the URL to fetch
     * @param nodeId       the node_id to stamp on the record
     * @return an unsigned ObservationRecord
     */
    static ObservationRecord observe(String targetUrl, String nodeId) throws Exception {
        HttpClient client = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NORMAL)
                .connectTimeout(TIMEOUT)
                .build();

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(targetUrl))
                .timeout(TIMEOUT)
                .GET()
                .build();

        Instant before = Instant.now();
        HttpResponse<byte[]> response = client.send(request, HttpResponse.BodyHandlers.ofByteArray());
        Instant after = Instant.now();

        long fetchMs = Duration.between(before, after).toMillis();
        byte[] body = response.body();
        String contentHash = sha256Hex(body);
        String finalUrl = response.uri().toString();

        ObservationRecord.Builder builder = ObservationRecord.builder()
                .version("0.1")
                .observedAt(before)
                .url(targetUrl)
                .finalUrl(finalUrl)
                .statusCode(response.statusCode())
                .fetchMs((int) fetchMs)
                .contentHash(contentHash)
                .nodeId(nodeId);

        // Capture selected headers
        for (String headerName : CAPTURED_HEADERS) {
            response.headers().firstValue(headerName)
                    .ifPresent(value -> builder.header(headerName, value));
        }

        // Extract directives
        builder.directiveRobotsHeader(
                response.headers().firstValue("x-robots-tag").orElse(null));

        // Parse outbound links and canonical/robots_meta from HTML if content-type is HTML
        String contentType = response.headers().firstValue("content-type").orElse("");
        if (contentType.contains("text/html")) {
            parseHtmlDirectivesAndLinks(new String(body, java.nio.charset.StandardCharsets.UTF_8),
                    finalUrl, builder);
        }

        return builder.build();
    }

    /**
     * Minimal HTML parsing for canonical, robots meta, and outbound links.
     * Intentionally simple — not a full HTML parser.
     */
    private static void parseHtmlDirectivesAndLinks(String html, String baseUrl,
                                                     ObservationRecord.Builder builder) {
        // Canonical link
        String canonical = extractMetaContent(html, "<link[^>]+rel=[\"']canonical[\"'][^>]+href=[\"']([^\"']+)[\"']");
        if (canonical == null) {
            canonical = extractMetaContent(html, "<link[^>]+href=[\"']([^\"']+)[\"'][^>]+rel=[\"']canonical[\"']");
        }
        builder.directiveCanonical(canonical);

        // Robots meta
        String robotsMeta = extractMetaContent(html,
                "<meta[^>]+name=[\"']robots[\"'][^>]+content=[\"']([^\"']+)[\"']");
        if (robotsMeta == null) {
            robotsMeta = extractMetaContent(html,
                    "<meta[^>]+content=[\"']([^\"']+)[\"'][^>]+name=[\"']robots[\"']");
        }
        builder.directiveRobotsMeta(robotsMeta);

        // Outbound links (href attributes from <a> tags)
        List<String> links = new ArrayList<>();
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("<a[^>]+href=[\"']([^\"'#]+)[\"']", java.util.regex.Pattern.CASE_INSENSITIVE)
                .matcher(html);
        while (m.find()) {
            String href = m.group(1).strip();
            if (href.startsWith("http://") || href.startsWith("https://")) {
                links.add(href);
            }
        }
        builder.links(links);
    }

    private static String extractMetaContent(String html, String regex) {
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile(regex, java.util.regex.Pattern.CASE_INSENSITIVE)
                .matcher(html);
        return m.find() ? m.group(1) : null;
    }

    private static String sha256Hex(byte[] data) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return MerkleTree.encodeHex(digest.digest(data));
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new AssertionError("SHA-256 must be available", e);
        }
    }
}
