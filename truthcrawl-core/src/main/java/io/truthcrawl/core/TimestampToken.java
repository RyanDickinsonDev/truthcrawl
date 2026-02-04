package io.truthcrawl.core;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * A signed attestation that a data hash existed at a given time.
 *
 * <p>Canonical text format:
 * <pre>
 * data_hash:abc123...
 * issued_at:2024-01-15T12:00:00Z
 * tsa_key_id:def456...
 * tsa_signature:Base64...
 * </pre>
 *
 * <p>The signing input is a versioned, deterministic byte sequence:
 * <pre>
 * truthcrawl-timestamp-v1\n
 * data_hash\n
 * issued_at\n
 * tsa_key_id\n
 * </pre>
 *
 * <p>The TSA signs the signing input (NOT the canonical text, which includes the signature).
 *
 * @param dataHash     64-char lowercase hex SHA-256 of the data being timestamped
 * @param issuedAt     when the timestamp was issued (UTC)
 * @param tsaKeyId     64-char lowercase hex SHA-256 fingerprint of the TSA's public key
 * @param tsaSignature Base64-encoded Ed25519 signature over the signing input
 */
public record TimestampToken(
        String dataHash,
        Instant issuedAt,
        String tsaKeyId,
        String tsaSignature
) {
    private static final String SIGNING_PREFIX = "truthcrawl-timestamp-v1";

    public TimestampToken {
        if (dataHash == null || dataHash.length() != 64) {
            throw new IllegalArgumentException("data_hash must be 64-char hex");
        }
        if (issuedAt == null) {
            throw new IllegalArgumentException("issued_at must not be null");
        }
        if (tsaKeyId == null || tsaKeyId.length() != 64) {
            throw new IllegalArgumentException("tsa_key_id must be 64-char hex");
        }
        if (tsaSignature == null || tsaSignature.isEmpty()) {
            throw new IllegalArgumentException("tsa_signature must not be empty");
        }
    }

    /**
     * The exact bytes that the TSA signs. Versioned to prevent cross-protocol reuse.
     */
    public byte[] signingInput() {
        String message = SIGNING_PREFIX + "\n"
                + dataHash + "\n"
                + DateTimeFormatter.ISO_INSTANT.format(issuedAt) + "\n"
                + tsaKeyId + "\n";
        return message.getBytes(StandardCharsets.UTF_8);
    }

    /**
     * Canonical text representation (includes signature).
     */
    public String toCanonicalText() {
        return "data_hash:" + dataHash + "\n"
                + "issued_at:" + DateTimeFormatter.ISO_INSTANT.format(issuedAt) + "\n"
                + "tsa_key_id:" + tsaKeyId + "\n"
                + "tsa_signature:" + tsaSignature + "\n";
    }

    /**
     * SHA-256 of the canonical text excluding the signature line.
     */
    public String tokenHash() {
        String hashInput = "data_hash:" + dataHash + "\n"
                + "issued_at:" + DateTimeFormatter.ISO_INSTANT.format(issuedAt) + "\n"
                + "tsa_key_id:" + tsaKeyId + "\n";
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(hashInput.getBytes(StandardCharsets.UTF_8));
            return MerkleTree.encodeHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new AssertionError("SHA-256 must be available", e);
        }
    }

    /**
     * Parse a timestamp token from canonical text lines.
     */
    public static TimestampToken parse(List<String> lines) {
        List<String> filtered = lines.stream()
                .map(String::strip)
                .filter(s -> !s.isEmpty())
                .toList();
        if (filtered.size() != 4) {
            throw new IllegalArgumentException(
                    "Expected 4 token lines, got " + filtered.size());
        }

        String dataHash = parseField(filtered.get(0), "data_hash");
        Instant issuedAt = Instant.parse(parseField(filtered.get(1), "issued_at"));
        String tsaKeyId = parseField(filtered.get(2), "tsa_key_id");
        String tsaSignature = parseField(filtered.get(3), "tsa_signature");

        return new TimestampToken(dataHash, issuedAt, tsaKeyId, tsaSignature);
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
