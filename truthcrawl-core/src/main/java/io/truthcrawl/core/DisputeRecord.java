package io.truthcrawl.core;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * A dispute filed against a published observation.
 *
 * <p>Canonical text format (fixed field order, newline-terminated):
 * <pre>
 * dispute_id:2024-01-16-0001
 * challenged_record_hash:abc123...
 * challenger_record_hash:def456...
 * url:https://example.com
 * filed_at:2024-01-16T10:00:00Z
 * challenger_node_id:789abc...
 * </pre>
 *
 * <p>The dispute hash is SHA-256 of the canonical text (excluding signature).
 * The challenger signs the canonical text with their node key.
 *
 * @param disputeId              unique identifier (date + sequence, e.g. "2024-01-16-0001")
 * @param challengedRecordHash   record hash of the observation being disputed
 * @param challengerRecordHash   record hash of the challenger's counter-observation
 * @param url                    the URL both observations are about
 * @param filedAt                when the dispute was filed (UTC)
 * @param challengerNodeId       node_id of the challenger
 * @param challengerSignature    Ed25519 signature over canonical text (nullable before signing)
 */
public record DisputeRecord(
        String disputeId,
        String challengedRecordHash,
        String challengerRecordHash,
        String url,
        Instant filedAt,
        String challengerNodeId,
        String challengerSignature
) {
    public DisputeRecord {
        if (disputeId == null || disputeId.isEmpty()) {
            throw new IllegalArgumentException("dispute_id must not be empty");
        }
        if (challengedRecordHash == null || challengedRecordHash.length() != 64) {
            throw new IllegalArgumentException("challenged_record_hash must be 64-char hex");
        }
        if (challengerRecordHash == null || challengerRecordHash.length() != 64) {
            throw new IllegalArgumentException("challenger_record_hash must be 64-char hex");
        }
        if (url == null || url.isEmpty()) {
            throw new IllegalArgumentException("url must not be empty");
        }
        if (filedAt == null) {
            throw new IllegalArgumentException("filed_at must not be null");
        }
        if (challengerNodeId == null || challengerNodeId.isEmpty()) {
            throw new IllegalArgumentException("challenger_node_id must not be empty");
        }
    }

    /**
     * Return a copy with the signature attached.
     */
    public DisputeRecord withSignature(String signature) {
        return new DisputeRecord(disputeId, challengedRecordHash, challengerRecordHash,
                url, filedAt, challengerNodeId, signature);
    }

    /**
     * Canonical text (excludes signature). This is what gets hashed and signed.
     */
    public String toCanonicalText() {
        return "dispute_id:" + disputeId + "\n"
                + "challenged_record_hash:" + challengedRecordHash + "\n"
                + "challenger_record_hash:" + challengerRecordHash + "\n"
                + "url:" + url + "\n"
                + "filed_at:" + DateTimeFormatter.ISO_INSTANT.format(filedAt) + "\n"
                + "challenger_node_id:" + challengerNodeId + "\n";
    }

    /**
     * Full text including signature (for storage).
     */
    public String toFullText() {
        return toCanonicalText()
                + "challenger_signature:" + (challengerSignature == null ? "" : challengerSignature) + "\n";
    }

    /**
     * SHA-256 of the canonical text, as lowercase hex.
     */
    public String disputeHash() {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(toCanonicalText().getBytes(StandardCharsets.UTF_8));
            return MerkleTree.encodeHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new AssertionError("SHA-256 must be available", e);
        }
    }

    /**
     * Parse from full text representation.
     */
    public static DisputeRecord parse(List<String> lines) {
        String disputeId = null, challengedHash = null, challengerHash = null;
        String url = null, challengerNodeId = null, signature = null;
        Instant filedAt = null;

        for (String line : lines) {
            if (line.isBlank()) continue;
            int colon = line.indexOf(':');
            if (colon == -1) throw new IllegalArgumentException("Missing ':' in line: " + line);
            String key = line.substring(0, colon);
            String value = line.substring(colon + 1);
            switch (key) {
                case "dispute_id" -> disputeId = value;
                case "challenged_record_hash" -> challengedHash = value;
                case "challenger_record_hash" -> challengerHash = value;
                case "url" -> url = value;
                case "filed_at" -> filedAt = Instant.parse(value);
                case "challenger_node_id" -> challengerNodeId = value;
                case "challenger_signature" -> signature = value.isEmpty() ? null : value;
                default -> throw new IllegalArgumentException("Unknown field: " + key);
            }
        }

        return new DisputeRecord(disputeId, challengedHash, challengerHash,
                url, filedAt, challengerNodeId, signature);
    }
}
