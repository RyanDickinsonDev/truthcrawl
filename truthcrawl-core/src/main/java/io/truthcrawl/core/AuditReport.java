package io.truthcrawl.core;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;

/**
 * An audit report summarizing verification results for a batch.
 *
 * <p>Canonical text format (fixed field order, newline-terminated):
 * <pre>
 * batch_id:2024-01-15
 * records_total:100
 * records_sampled:10
 * records_matched:9
 * records_mismatched:1
 * disputes_filed:1
 * </pre>
 *
 * <p>Reports are deterministic: same inputs always produce the same report.
 *
 * @param batchId           the batch being audited
 * @param recordsTotal      total records in the batch
 * @param recordsSampled    number of records sampled for verification
 * @param recordsMatched    number of sampled records that matched
 * @param recordsMismatched number of sampled records with discrepancies
 * @param disputesFiled     number of disputes filed from mismatches
 */
public record AuditReport(
        String batchId,
        int recordsTotal,
        int recordsSampled,
        int recordsMatched,
        int recordsMismatched,
        int disputesFiled
) {
    public AuditReport {
        if (batchId == null || !batchId.matches("\\d{4}-\\d{2}-\\d{2}")) {
            throw new IllegalArgumentException("batch_id must be YYYY-MM-DD, got: " + batchId);
        }
        if (recordsTotal < 0) {
            throw new IllegalArgumentException("records_total must be >= 0");
        }
        if (recordsSampled < 0 || recordsSampled > recordsTotal) {
            throw new IllegalArgumentException("records_sampled must be in [0, records_total]");
        }
        if (recordsMatched < 0 || recordsMismatched < 0) {
            throw new IllegalArgumentException("match/mismatch counts must be >= 0");
        }
        if (recordsMatched + recordsMismatched != recordsSampled) {
            throw new IllegalArgumentException(
                    "matched + mismatched must equal sampled: "
                            + recordsMatched + " + " + recordsMismatched
                            + " != " + recordsSampled);
        }
        if (disputesFiled < 0 || disputesFiled > recordsMismatched) {
            throw new IllegalArgumentException("disputes_filed must be in [0, records_mismatched]");
        }
    }

    /**
     * Canonical text representation.
     */
    public String toCanonicalText() {
        return "batch_id:" + batchId + "\n"
                + "records_total:" + recordsTotal + "\n"
                + "records_sampled:" + recordsSampled + "\n"
                + "records_matched:" + recordsMatched + "\n"
                + "records_mismatched:" + recordsMismatched + "\n"
                + "disputes_filed:" + disputesFiled + "\n";
    }

    /**
     * SHA-256 of the canonical text, as lowercase hex.
     */
    public String reportHash() {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(toCanonicalText().getBytes(StandardCharsets.UTF_8));
            return MerkleTree.encodeHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new AssertionError("SHA-256 must be available", e);
        }
    }

    /**
     * Parse an audit report from its canonical text.
     */
    public static AuditReport parse(List<String> lines) {
        List<String> filtered = lines.stream()
                .map(String::strip)
                .filter(s -> !s.isEmpty())
                .toList();
        if (filtered.size() != 6) {
            throw new IllegalArgumentException(
                    "Expected 6 report lines, got " + filtered.size());
        }

        String batchId = parseField(filtered.get(0), "batch_id");
        int total = Integer.parseInt(parseField(filtered.get(1), "records_total"));
        int sampled = Integer.parseInt(parseField(filtered.get(2), "records_sampled"));
        int matched = Integer.parseInt(parseField(filtered.get(3), "records_matched"));
        int mismatched = Integer.parseInt(parseField(filtered.get(4), "records_mismatched"));
        int disputes = Integer.parseInt(parseField(filtered.get(5), "disputes_filed"));

        return new AuditReport(batchId, total, sampled, matched, mismatched, disputes);
    }

    /**
     * Format as human-readable summary.
     */
    public String formatReport() {
        return "Audit Report: " + batchId + "\n"
                + "  Total records:  " + recordsTotal + "\n"
                + "  Sampled:        " + recordsSampled + "\n"
                + "  Matched:        " + recordsMatched + "\n"
                + "  Mismatched:     " + recordsMismatched + "\n"
                + "  Disputes filed: " + disputesFiled + "\n";
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
