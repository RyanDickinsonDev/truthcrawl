package io.truthcrawl.core;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A batch manifest: an ordered list of record hashes for one transparency log batch.
 *
 * <p>Canonical text format (one lowercase hex SHA-256 hash per line, sorted, newline-terminated):
 * <pre>
 * 2e7d2c03...
 * 3e23e816...
 * ca978112...
 * </pre>
 *
 * <p>The manifest hash is SHA-256 of the canonical text representation (UTF-8 bytes).
 * This binds the exact contents and order to the batch metadata.
 *
 * <p>Rules:
 * <ul>
 *   <li>Hashes must be valid 64-character lowercase hex strings.</li>
 *   <li>Entries are sorted lexicographically and deduplicated.</li>
 *   <li>Empty manifests are not allowed.</li>
 * </ul>
 */
public final class BatchManifest {

    private final List<String> entries;

    private BatchManifest(List<String> entries) {
        this.entries = entries;
    }

    /**
     * Create a manifest from raw hash strings. Validates, sorts, and deduplicates.
     *
     * @param hashes non-empty list of 64-char lowercase hex strings
     * @return a new BatchManifest
     * @throws IllegalArgumentException if empty or contains invalid hashes
     */
    public static BatchManifest of(List<String> hashes) {
        if (hashes.isEmpty()) {
            throw new IllegalArgumentException("Manifest must not be empty");
        }
        for (String h : hashes) {
            MerkleTree.decodeHex(h); // validates format
        }
        List<String> sorted = new ArrayList<>(hashes.stream().distinct().sorted().toList());
        return new BatchManifest(Collections.unmodifiableList(sorted));
    }

    /**
     * Parse a manifest from its canonical text representation.
     *
     * @param lines non-blank lines from the manifest file
     * @return a new BatchManifest
     * @throws IllegalArgumentException if empty or contains invalid hashes
     */
    public static BatchManifest parse(List<String> lines) {
        List<String> filtered = lines.stream()
                .map(String::strip)
                .filter(s -> !s.isEmpty())
                .toList();
        return of(filtered);
    }

    /**
     * The sorted, deduplicated record hashes.
     */
    public List<String> entries() {
        return entries;
    }

    /**
     * Number of records in this manifest.
     */
    public int size() {
        return entries.size();
    }

    /**
     * Canonical text representation: sorted entries, one per line, newline-terminated.
     */
    public String toCanonicalText() {
        StringBuilder sb = new StringBuilder();
        for (String entry : entries) {
            sb.append(entry).append('\n');
        }
        return sb.toString();
    }

    /**
     * SHA-256 hash of the canonical text representation (UTF-8), returned as lowercase hex.
     */
    public String manifestHash() {
        byte[] bytes = toCanonicalText().getBytes(StandardCharsets.UTF_8);
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return MerkleTree.encodeHex(digest.digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new AssertionError("SHA-256 must be available", e);
        }
    }

    /**
     * Compute the Merkle root of this manifest's entries.
     */
    public String merkleRoot() {
        return MerkleTree.computeRoot(entries);
    }
}
