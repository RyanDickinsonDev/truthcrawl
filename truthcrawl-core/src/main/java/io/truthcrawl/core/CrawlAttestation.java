package io.truthcrawl.core;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A signed declaration of which domains a node crawls.
 *
 * <p>Canonical text format:
 * <pre>
 * node_id:{64-char hex}
 * attested_at:{ISO-8601 UTC}
 * domain:{domain1}
 * domain:{domain2}
 * ...
 * attestation_signature:{Base64 Ed25519 signature}
 * </pre>
 *
 * <p>The signing input is a versioned, deterministic byte sequence:
 * <pre>
 * truthcrawl-attestation-v1\n
 * node_id\n
 * attested_at\n
 * domain1\n
 * domain2\n
 * ...
 * </pre>
 *
 * <p>Domains are sorted alphabetically. At least one domain is required.
 *
 * @param nodeId                64-char lowercase hex SHA-256 fingerprint of the node's public key
 * @param attestedAt            when the attestation was created (UTC)
 * @param domains               sorted list of domains (lowercase, no protocol prefix)
 * @param attestationSignature  Base64-encoded Ed25519 signature over the signing input
 */
public record CrawlAttestation(
        String nodeId,
        Instant attestedAt,
        List<String> domains,
        String attestationSignature
) {
    private static final String SIGNING_PREFIX = "truthcrawl-attestation-v1";

    public CrawlAttestation {
        if (nodeId == null || nodeId.length() != 64) {
            throw new IllegalArgumentException("node_id must be 64-char hex");
        }
        if (attestedAt == null) {
            throw new IllegalArgumentException("attested_at must not be null");
        }
        if (domains == null || domains.isEmpty()) {
            throw new IllegalArgumentException("at least one domain is required");
        }
        // Ensure sorted and immutable
        List<String> sorted = new ArrayList<>(domains);
        Collections.sort(sorted);
        domains = Collections.unmodifiableList(sorted);
        if (attestationSignature == null || attestationSignature.isEmpty()) {
            throw new IllegalArgumentException("attestation_signature must not be empty");
        }
    }

    /**
     * The exact bytes that the node signs. Versioned to prevent cross-protocol reuse.
     */
    public byte[] signingInput() {
        StringBuilder sb = new StringBuilder();
        sb.append(SIGNING_PREFIX).append("\n");
        sb.append(nodeId).append("\n");
        sb.append(DateTimeFormatter.ISO_INSTANT.format(attestedAt)).append("\n");
        for (String domain : domains) {
            sb.append(domain).append("\n");
        }
        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    /**
     * Canonical text representation (includes signature).
     */
    public String toCanonicalText() {
        StringBuilder sb = new StringBuilder();
        sb.append("node_id:").append(nodeId).append("\n");
        sb.append("attested_at:").append(DateTimeFormatter.ISO_INSTANT.format(attestedAt)).append("\n");
        for (String domain : domains) {
            sb.append("domain:").append(domain).append("\n");
        }
        sb.append("attestation_signature:").append(attestationSignature).append("\n");
        return sb.toString();
    }

    /**
     * Create a signed attestation.
     *
     * @param nodeKey    the node's Ed25519 key pair (must have private key)
     * @param domains    list of domains to attest (will be sorted and lowercased)
     * @param attestedAt timestamp for the attestation
     * @return a signed CrawlAttestation
     */
    public static CrawlAttestation create(PublisherKey nodeKey,
                                          List<String> domains,
                                          Instant attestedAt) {
        String nodeId = RequestSigner.computeNodeId(nodeKey);

        // Normalize: lowercase, sort
        List<String> normalized = new ArrayList<>();
        for (String d : domains) {
            normalized.add(d.toLowerCase());
        }
        Collections.sort(normalized);

        // Build signing input
        StringBuilder sb = new StringBuilder();
        sb.append(SIGNING_PREFIX).append("\n");
        sb.append(nodeId).append("\n");
        sb.append(DateTimeFormatter.ISO_INSTANT.format(attestedAt)).append("\n");
        for (String domain : normalized) {
            sb.append(domain).append("\n");
        }
        byte[] signingInput = sb.toString().getBytes(StandardCharsets.UTF_8);
        String signature = nodeKey.sign(signingInput);

        return new CrawlAttestation(nodeId, attestedAt, normalized, signature);
    }

    /**
     * Parse a crawl attestation from canonical text lines.
     */
    public static CrawlAttestation parse(List<String> lines) {
        List<String> filtered = lines.stream()
                .map(String::strip)
                .filter(s -> !s.isEmpty())
                .toList();
        if (filtered.size() < 4) {
            throw new IllegalArgumentException(
                    "Expected at least 4 attestation lines (node_id, attested_at, 1+ domain, signature), got " + filtered.size());
        }

        String nodeId = parseField(filtered.get(0), "node_id");
        Instant attestedAt = Instant.parse(parseField(filtered.get(1), "attested_at"));

        List<String> domains = new ArrayList<>();
        int i = 2;
        while (i < filtered.size() - 1) {
            String line = filtered.get(i);
            if (!line.startsWith("domain:")) {
                throw new IllegalArgumentException("Expected 'domain:' line, got: " + line);
            }
            domains.add(line.substring("domain:".length()));
            i++;
        }

        String signature = parseField(filtered.get(filtered.size() - 1), "attestation_signature");

        return new CrawlAttestation(nodeId, attestedAt, domains, signature);
    }

    private static String parseField(String line, String expectedKey) {
        int colon = line.indexOf(':');
        if (colon == -1) {
            throw new IllegalArgumentException("Missing ':' in line: " + line);
        }
        String key = line.substring(0, colon);
        if (!key.equals(expectedKey)) {
            throw new IllegalArgumentException(
                    "Expected key '" + expectedKey + "', got '" + key + "'");
        }
        return line.substring(colon + 1);
    }
}
