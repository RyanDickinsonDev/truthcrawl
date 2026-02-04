package io.truthcrawl.core;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Batch metadata for one transparency log entry.
 *
 * <p>Canonical text format (fixed key order, newline-terminated):
 * <pre>
 * batch_id:2024-01-15
 * merkle_root:d31a37ef...
 * manifest_hash:abc123...
 * record_count:3
 * </pre>
 *
 * <p>The signing input is a well-defined byte sequence:
 * <pre>
 * truthcrawl-batch-v1\n
 * batch_id\n
 * merkle_root\n
 * manifest_hash\n
 * record_count\n
 * </pre>
 * This prefix prevents cross-protocol signature reuse.
 *
 * @param batchId      date string in YYYY-MM-DD format
 * @param merkleRoot   lowercase hex Merkle root of the manifest entries
 * @param manifestHash lowercase hex SHA-256 of the manifest canonical text
 * @param recordCount  number of records in the manifest
 */
public record BatchMetadata(
        String batchId,
        String merkleRoot,
        String manifestHash,
        int recordCount
) {
    private static final String SIGNING_PREFIX = "truthcrawl-batch-v1";

    public BatchMetadata {
        if (batchId == null || !batchId.matches("\\d{4}-\\d{2}-\\d{2}")) {
            throw new IllegalArgumentException("batch_id must be YYYY-MM-DD, got: " + batchId);
        }
        if (merkleRoot == null || merkleRoot.length() != 64) {
            throw new IllegalArgumentException("merkle_root must be 64-char hex");
        }
        if (manifestHash == null || manifestHash.length() != 64) {
            throw new IllegalArgumentException("manifest_hash must be 64-char hex");
        }
        if (recordCount < 1) {
            throw new IllegalArgumentException("record_count must be >= 1, got: " + recordCount);
        }
    }

    /**
     * Build metadata from a manifest.
     */
    public static BatchMetadata fromManifest(String batchId, BatchManifest manifest) {
        return new BatchMetadata(
                batchId,
                manifest.merkleRoot(),
                manifest.manifestHash(),
                manifest.size()
        );
    }

    /**
     * Canonical text representation (human-readable, for storage).
     */
    public String toCanonicalText() {
        return "batch_id:" + batchId + "\n"
                + "merkle_root:" + merkleRoot + "\n"
                + "manifest_hash:" + manifestHash + "\n"
                + "record_count:" + recordCount + "\n";
    }

    /**
     * Parse metadata from its canonical text representation.
     *
     * @param lines non-blank lines from the metadata file
     * @return parsed BatchMetadata
     * @throws IllegalArgumentException if format is invalid
     */
    public static BatchMetadata parse(List<String> lines) {
        List<String> filtered = lines.stream()
                .map(String::strip)
                .filter(s -> !s.isEmpty())
                .toList();
        if (filtered.size() != 4) {
            throw new IllegalArgumentException(
                    "Expected 4 metadata lines, got " + filtered.size());
        }

        String batchId = parseField(filtered.get(0), "batch_id");
        String merkleRoot = parseField(filtered.get(1), "merkle_root");
        String manifestHash = parseField(filtered.get(2), "manifest_hash");
        int recordCount = Integer.parseInt(parseField(filtered.get(3), "record_count"));

        return new BatchMetadata(batchId, merkleRoot, manifestHash, recordCount);
    }

    /**
     * The exact bytes that must be signed. Deterministic, versioned, unambiguous.
     */
    public byte[] signingInput() {
        String message = SIGNING_PREFIX + "\n"
                + batchId + "\n"
                + merkleRoot + "\n"
                + manifestHash + "\n"
                + recordCount + "\n";
        return message.getBytes(StandardCharsets.UTF_8);
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
