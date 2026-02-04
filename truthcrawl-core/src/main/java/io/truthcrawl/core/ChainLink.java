package io.truthcrawl.core;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;

/**
 * A link in the batch chain. Wraps batch metadata with a back-reference
 * to the previous batch's Merkle root, forming an append-only chain.
 *
 * <p>Canonical text format (fixed field order, newline-terminated):
 * <pre>
 * batch_id:2024-01-15
 * merkle_root:abc123...
 * manifest_hash:def456...
 * record_count:3
 * previous_root:000000...
 * </pre>
 *
 * <p>The signing input uses a versioned prefix to prevent cross-protocol reuse:
 * <pre>
 * truthcrawl-chain-v1\n
 * batch_id\n
 * merkle_root\n
 * manifest_hash\n
 * record_count\n
 * previous_root\n
 * </pre>
 *
 * @param batchId      date string in YYYY-MM-DD format
 * @param merkleRoot   lowercase hex Merkle root of the manifest entries
 * @param manifestHash lowercase hex SHA-256 of the manifest canonical text
 * @param recordCount  number of records in the manifest
 * @param previousRoot lowercase hex Merkle root of the previous batch (64 zeros for genesis)
 */
public record ChainLink(
        String batchId,
        String merkleRoot,
        String manifestHash,
        int recordCount,
        String previousRoot
) {
    /** Previous root value for the first link in a chain. */
    public static final String GENESIS_ROOT =
            "0000000000000000000000000000000000000000000000000000000000000000";

    private static final String SIGNING_PREFIX = "truthcrawl-chain-v1";

    public ChainLink {
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
        if (previousRoot == null || previousRoot.length() != 64) {
            throw new IllegalArgumentException("previous_root must be 64-char hex");
        }
    }

    /**
     * Build a chain link from a manifest and previous root.
     */
    public static ChainLink fromManifest(String batchId, BatchManifest manifest,
                                          String previousRoot) {
        return new ChainLink(
                batchId,
                manifest.merkleRoot(),
                manifest.manifestHash(),
                manifest.size(),
                previousRoot
        );
    }

    /**
     * Whether this is the genesis (first) link in a chain.
     */
    public boolean isGenesis() {
        return GENESIS_ROOT.equals(previousRoot);
    }

    /**
     * Canonical text representation.
     */
    public String toCanonicalText() {
        return "batch_id:" + batchId + "\n"
                + "merkle_root:" + merkleRoot + "\n"
                + "manifest_hash:" + manifestHash + "\n"
                + "record_count:" + recordCount + "\n"
                + "previous_root:" + previousRoot + "\n";
    }

    /**
     * The exact bytes that must be signed. Versioned to prevent cross-protocol reuse.
     */
    public byte[] signingInput() {
        String message = SIGNING_PREFIX + "\n"
                + batchId + "\n"
                + merkleRoot + "\n"
                + manifestHash + "\n"
                + recordCount + "\n"
                + previousRoot + "\n";
        return message.getBytes(StandardCharsets.UTF_8);
    }

    /**
     * SHA-256 of the canonical text, as lowercase hex.
     */
    public String linkHash() {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(toCanonicalText().getBytes(StandardCharsets.UTF_8));
            return MerkleTree.encodeHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new AssertionError("SHA-256 must be available", e);
        }
    }

    /**
     * Parse a chain link from its canonical text.
     */
    public static ChainLink parse(List<String> lines) {
        List<String> filtered = lines.stream()
                .map(String::strip)
                .filter(s -> !s.isEmpty())
                .toList();
        if (filtered.size() != 5) {
            throw new IllegalArgumentException(
                    "Expected 5 chain link lines, got " + filtered.size());
        }

        String batchId = parseField(filtered.get(0), "batch_id");
        String merkleRoot = parseField(filtered.get(1), "merkle_root");
        String manifestHash = parseField(filtered.get(2), "manifest_hash");
        int recordCount = Integer.parseInt(parseField(filtered.get(3), "record_count"));
        String previousRoot = parseField(filtered.get(4), "previous_root");

        return new ChainLink(batchId, merkleRoot, manifestHash, recordCount, previousRoot);
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
