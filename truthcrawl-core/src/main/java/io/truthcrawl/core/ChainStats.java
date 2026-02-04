package io.truthcrawl.core;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;

/**
 * Aggregate statistics computed from a record store.
 *
 * <p>Canonical text format:
 * <pre>
 * total_batches:5
 * total_records:150
 * unique_urls:42
 * unique_nodes:8
 * </pre>
 *
 * @param totalBatches  number of batches in the chain
 * @param totalRecords  total observation records in the store
 * @param uniqueUrls    distinct URLs observed
 * @param uniqueNodes   distinct node IDs
 */
public record ChainStats(
        int totalBatches,
        int totalRecords,
        int uniqueUrls,
        int uniqueNodes
) {
    /**
     * Compute statistics from a batch chain length and record store index.
     *
     * @param chainLength number of batches in the chain
     * @param index       the built index from the record store
     * @return computed statistics
     */
    public static ChainStats compute(int chainLength, IndexBuilder.Index index) {
        return new ChainStats(
                chainLength,
                index.urlIndex().values().stream().mapToInt(List::size).sum(),
                index.urls().size(),
                index.nodeIds().size()
        );
    }

    /**
     * Compute statistics directly from a record store.
     *
     * @param chainLength number of batches in the chain
     * @param store       the record store
     * @return computed statistics
     * @throws IOException if reading fails
     */
    public static ChainStats compute(int chainLength, RecordStore store) throws IOException {
        IndexBuilder.Index index = IndexBuilder.build(store);
        return compute(chainLength, index);
    }

    /**
     * Canonical text representation.
     */
    public String toCanonicalText() {
        return "total_batches:" + totalBatches + "\n"
                + "total_records:" + totalRecords + "\n"
                + "unique_urls:" + uniqueUrls + "\n"
                + "unique_nodes:" + uniqueNodes + "\n";
    }

    /**
     * SHA-256 of the canonical text, as lowercase hex.
     */
    public String statsHash() {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(toCanonicalText().getBytes(StandardCharsets.UTF_8));
            return MerkleTree.encodeHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new AssertionError("SHA-256 must be available", e);
        }
    }

    /**
     * Parse from canonical text.
     */
    public static ChainStats parse(java.util.List<String> lines) {
        java.util.List<String> filtered = lines.stream()
                .map(String::strip)
                .filter(s -> !s.isEmpty())
                .toList();
        if (filtered.size() != 4) {
            throw new IllegalArgumentException(
                    "Expected 4 stats lines, got " + filtered.size());
        }

        int batches = Integer.parseInt(parseField(filtered.get(0), "total_batches"));
        int records = Integer.parseInt(parseField(filtered.get(1), "total_records"));
        int urls = Integer.parseInt(parseField(filtered.get(2), "unique_urls"));
        int nodes = Integer.parseInt(parseField(filtered.get(3), "unique_nodes"));

        return new ChainStats(batches, records, urls, nodes);
    }

    /**
     * Format as human-readable summary.
     */
    public String formatReport() {
        return "Chain Statistics\n"
                + "  Batches:      " + totalBatches + "\n"
                + "  Records:      " + totalRecords + "\n"
                + "  Unique URLs:  " + uniqueUrls + "\n"
                + "  Unique nodes: " + uniqueNodes + "\n";
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
