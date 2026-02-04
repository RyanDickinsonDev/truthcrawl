package io.truthcrawl.core;

import java.util.List;

/**
 * An immutable record of a peer node in the truthcrawl network.
 *
 * <p>Canonical text format:
 * <pre>
 * node_id:{64-char hex}
 * endpoint:{URL}
 * public_key:{Base64}
 * </pre>
 *
 * @param nodeId         SHA-256 fingerprint of the peer's Ed25519 public key (64-char lowercase hex)
 * @param endpointUrl    the peer's HTTP endpoint URL
 * @param publicKeyBase64 the peer's Ed25519 public key (Base64-encoded)
 */
public record PeerInfo(
        String nodeId,
        String endpointUrl,
        String publicKeyBase64
) {

    public PeerInfo {
        if (nodeId == null || nodeId.length() != 64) {
            throw new IllegalArgumentException("nodeId must be 64-char hex");
        }
        if (endpointUrl == null || endpointUrl.isEmpty()) {
            throw new IllegalArgumentException("endpointUrl must not be empty");
        }
        if (publicKeyBase64 == null || publicKeyBase64.isEmpty()) {
            throw new IllegalArgumentException("publicKeyBase64 must not be empty");
        }
    }

    /**
     * Canonical text representation.
     */
    public String toCanonicalText() {
        return "node_id:" + nodeId + "\n"
                + "endpoint:" + endpointUrl + "\n"
                + "public_key:" + publicKeyBase64 + "\n";
    }

    /**
     * Parse a PeerInfo from canonical text lines.
     */
    public static PeerInfo parse(List<String> lines) {
        List<String> filtered = lines.stream()
                .map(String::strip)
                .filter(s -> !s.isEmpty())
                .toList();
        if (filtered.size() != 3) {
            throw new IllegalArgumentException(
                    "Expected 3 peer info lines, got " + filtered.size());
        }

        String nodeId = parseField(filtered.get(0), "node_id");
        String endpointUrl = parseField(filtered.get(1), "endpoint");
        String publicKeyBase64 = parseField(filtered.get(2), "public_key");

        return new PeerInfo(nodeId, endpointUrl, publicKeyBase64);
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
