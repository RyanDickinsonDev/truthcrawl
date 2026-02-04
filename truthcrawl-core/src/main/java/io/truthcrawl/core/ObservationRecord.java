package io.truthcrawl.core;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * A crawl observation record per docs/observation-schema.md.
 *
 * <p>Canonical text format (fixed field order, one per line):
 * <pre>
 * version:0.1
 * observed_at:2024-01-15T12:00:00Z
 * url:https://example.com
 * final_url:https://example.com/
 * status_code:200
 * fetch_ms:142
 * content_hash:abc123...
 * header:content-type:text/html
 * header:server:nginx
 * directive:canonical:https://example.com/
 * directive:robots_meta:index,follow
 * directive:robots_header:
 * link:https://example.com/about
 * link:https://example.com/contact
 * node_id:def456...
 * </pre>
 *
 * <p>Rules:
 * <ul>
 *   <li>Null/empty values: written as {@code field:} (empty after colon)</li>
 *   <li>Headers: sorted by key, lowercase keys, one {@code header:key:value} line each</li>
 *   <li>Links: sorted, deduplicated, one {@code link:url} line each</li>
 *   <li>node.signature is NOT part of the canonical form (it signs this form)</li>
 * </ul>
 */
public final class ObservationRecord {

    private final String version;
    private final Instant observedAt;
    private final String url;
    private final String finalUrl;
    private final int statusCode;
    private final int fetchMs;
    private final String contentHash;
    private final Map<String, String> headersSubset;  // sorted by key
    private final String directiveCanonical;           // nullable
    private final String directiveRobotsMeta;          // nullable
    private final String directiveRobotsHeader;        // nullable
    private final List<String> outboundLinks;          // sorted, deduped
    private final String nodeId;
    private final String nodeSignature;                // nullable, not part of canonical form

    private ObservationRecord(Builder builder) {
        this.version = requireNonEmpty(builder.version, "version");
        this.observedAt = requireNonNull(builder.observedAt, "observed_at");
        this.url = requireNonEmpty(builder.url, "url");
        this.finalUrl = requireNonEmpty(builder.finalUrl, "final_url");
        this.statusCode = builder.statusCode;
        this.fetchMs = builder.fetchMs;
        this.contentHash = requireNonEmpty(builder.contentHash, "content_hash");
        this.headersSubset = Collections.unmodifiableMap(new TreeMap<>(builder.headersSubset));
        this.directiveCanonical = builder.directiveCanonical;
        this.directiveRobotsMeta = builder.directiveRobotsMeta;
        this.directiveRobotsHeader = builder.directiveRobotsHeader;
        this.outboundLinks = Collections.unmodifiableList(
                builder.outboundLinks.stream().distinct().sorted().toList());
        this.nodeId = requireNonEmpty(builder.nodeId, "node_id");
        this.nodeSignature = builder.nodeSignature;
    }

    public String version() { return version; }
    public Instant observedAt() { return observedAt; }
    public String url() { return url; }
    public String finalUrl() { return finalUrl; }
    public int statusCode() { return statusCode; }
    public int fetchMs() { return fetchMs; }
    public String contentHash() { return contentHash; }
    public Map<String, String> headersSubset() { return headersSubset; }
    public String directiveCanonical() { return directiveCanonical; }
    public String directiveRobotsMeta() { return directiveRobotsMeta; }
    public String directiveRobotsHeader() { return directiveRobotsHeader; }
    public List<String> outboundLinks() { return outboundLinks; }
    public String nodeId() { return nodeId; }
    public String nodeSignature() { return nodeSignature; }

    /**
     * Return a copy of this record with the given signature attached.
     */
    public ObservationRecord withSignature(String signature) {
        Builder b = toBuilder();
        b.nodeSignature = signature;
        return new ObservationRecord(b);
    }

    /**
     * Canonical text form (excludes node.signature). This is what gets hashed and signed.
     */
    public String toCanonicalText() {
        StringBuilder sb = new StringBuilder();
        sb.append("version:").append(version).append('\n');
        sb.append("observed_at:").append(DateTimeFormatter.ISO_INSTANT.format(observedAt)).append('\n');
        sb.append("url:").append(url).append('\n');
        sb.append("final_url:").append(finalUrl).append('\n');
        sb.append("status_code:").append(statusCode).append('\n');
        sb.append("fetch_ms:").append(fetchMs).append('\n');
        sb.append("content_hash:").append(contentHash).append('\n');
        for (Map.Entry<String, String> e : headersSubset.entrySet()) {
            sb.append("header:").append(e.getKey()).append(':').append(e.getValue()).append('\n');
        }
        sb.append("directive:canonical:").append(nullSafe(directiveCanonical)).append('\n');
        sb.append("directive:robots_meta:").append(nullSafe(directiveRobotsMeta)).append('\n');
        sb.append("directive:robots_header:").append(nullSafe(directiveRobotsHeader)).append('\n');
        for (String link : outboundLinks) {
            sb.append("link:").append(link).append('\n');
        }
        sb.append("node_id:").append(nodeId).append('\n');
        return sb.toString();
    }

    /**
     * Full text form including signature (for storage/transmission).
     */
    public String toFullText() {
        return toCanonicalText()
                + "node_signature:" + nullSafe(nodeSignature) + "\n";
    }

    /**
     * SHA-256 hash of the canonical text (UTF-8), as lowercase hex.
     */
    public String recordHash() {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(toCanonicalText().getBytes(StandardCharsets.UTF_8));
            return MerkleTree.encodeHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new AssertionError("SHA-256 must be available", e);
        }
    }

    /**
     * Parse a record from its full text representation (including signature line).
     */
    public static ObservationRecord parse(List<String> lines) {
        Builder b = builder();
        for (String line : lines) {
            if (line.isBlank()) continue;
            int colon = line.indexOf(':');
            if (colon == -1) throw new IllegalArgumentException("Missing ':' in line: " + line);
            String key = line.substring(0, colon);
            String value = line.substring(colon + 1);
            switch (key) {
                case "version" -> b.version(value);
                case "observed_at" -> b.observedAt(Instant.parse(value));
                case "url" -> b.url(value);
                case "final_url" -> b.finalUrl(value);
                case "status_code" -> b.statusCode(Integer.parseInt(value));
                case "fetch_ms" -> b.fetchMs(Integer.parseInt(value));
                case "content_hash" -> b.contentHash(value);
                case "header" -> {
                    int hColon = value.indexOf(':');
                    if (hColon == -1) throw new IllegalArgumentException("Bad header line: " + line);
                    b.header(value.substring(0, hColon), value.substring(hColon + 1));
                }
                case "directive" -> {
                    int dColon = value.indexOf(':');
                    if (dColon == -1) throw new IllegalArgumentException("Bad directive line: " + line);
                    String dName = value.substring(0, dColon);
                    String dVal = value.substring(dColon + 1);
                    String parsed = dVal.isEmpty() ? null : dVal;
                    switch (dName) {
                        case "canonical" -> b.directiveCanonical(parsed);
                        case "robots_meta" -> b.directiveRobotsMeta(parsed);
                        case "robots_header" -> b.directiveRobotsHeader(parsed);
                        default -> throw new IllegalArgumentException("Unknown directive: " + dName);
                    }
                }
                case "link" -> b.link(value);
                case "node_id" -> b.nodeId(value);
                case "node_signature" -> b.nodeSignature = value.isEmpty() ? null : value;
                default -> throw new IllegalArgumentException("Unknown field: " + key);
            }
        }
        return b.build();
    }

    public static Builder builder() {
        return new Builder();
    }

    private Builder toBuilder() {
        Builder b = new Builder();
        b.version = version;
        b.observedAt = observedAt;
        b.url = url;
        b.finalUrl = finalUrl;
        b.statusCode = statusCode;
        b.fetchMs = fetchMs;
        b.contentHash = contentHash;
        b.headersSubset = new TreeMap<>(headersSubset);
        b.directiveCanonical = directiveCanonical;
        b.directiveRobotsMeta = directiveRobotsMeta;
        b.directiveRobotsHeader = directiveRobotsHeader;
        b.outboundLinks = new ArrayList<>(outboundLinks);
        b.nodeId = nodeId;
        b.nodeSignature = nodeSignature;
        return b;
    }

    private static String nullSafe(String s) {
        return s == null ? "" : s;
    }

    private static String requireNonEmpty(String value, String name) {
        if (value == null || value.isEmpty()) {
            throw new IllegalArgumentException(name + " must not be empty");
        }
        return value;
    }

    private static <T> T requireNonNull(T value, String name) {
        if (value == null) {
            throw new IllegalArgumentException(name + " must not be null");
        }
        return value;
    }

    public static final class Builder {
        private String version;
        private Instant observedAt;
        private String url;
        private String finalUrl;
        private int statusCode;
        private int fetchMs;
        private String contentHash;
        private Map<String, String> headersSubset = new TreeMap<>();
        private String directiveCanonical;
        private String directiveRobotsMeta;
        private String directiveRobotsHeader;
        private List<String> outboundLinks = new ArrayList<>();
        private String nodeId;
        private String nodeSignature;

        private Builder() {}

        public Builder version(String v) { this.version = v; return this; }
        public Builder observedAt(Instant t) { this.observedAt = t; return this; }
        public Builder url(String u) { this.url = u; return this; }
        public Builder finalUrl(String u) { this.finalUrl = u; return this; }
        public Builder statusCode(int c) { this.statusCode = c; return this; }
        public Builder fetchMs(int ms) { this.fetchMs = ms; return this; }
        public Builder contentHash(String h) { this.contentHash = h; return this; }
        public Builder header(String key, String value) {
            this.headersSubset.put(key.toLowerCase(), value);
            return this;
        }
        public Builder directiveCanonical(String v) { this.directiveCanonical = v; return this; }
        public Builder directiveRobotsMeta(String v) { this.directiveRobotsMeta = v; return this; }
        public Builder directiveRobotsHeader(String v) { this.directiveRobotsHeader = v; return this; }
        public Builder link(String url) { this.outboundLinks.add(url); return this; }
        public Builder links(List<String> urls) { this.outboundLinks.addAll(urls); return this; }
        public Builder nodeId(String id) { this.nodeId = id; return this; }

        public ObservationRecord build() {
            return new ObservationRecord(this);
        }
    }
}
