package io.truthcrawl.core;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Tracks verification status for a batch.
 *
 * <p>Batch status values:
 * <ul>
 *   <li>PENDING: not yet verified</li>
 *   <li>VERIFIED_CLEAN: all checked records matched</li>
 *   <li>VERIFIED_WITH_DISPUTES: some records had discrepancies</li>
 *   <li>UNVERIFIABLE: insufficient independent data to verify</li>
 * </ul>
 *
 * <p>Canonical text format:
 * <pre>
 * batch_id:2024-01-15
 * batch_status:VERIFIED_CLEAN
 * records_total:100
 * records_checked:10
 * records_matched:10
 * records_mismatched:0
 * records_unverifiable:0
 * checked_at:2024-01-16T10:00:00Z
 * </pre>
 *
 * <p>Status is deterministic: same pipeline result produces the same status.
 * Status files are stored in a verification directory: {@code verification/{batch-id}.txt}
 *
 * @param batchId              the batch identifier (YYYY-MM-DD)
 * @param batchStatus          overall batch verification outcome
 * @param recordsTotal         total records in the batch
 * @param recordsChecked       records that were compared against independent data
 * @param recordsMatched       records that matched
 * @param recordsMismatched    records with discrepancies
 * @param recordsUnverifiable  records with no independent data
 * @param checkedAt            when verification was performed (UTC)
 */
public record VerificationStatus(
        String batchId,
        BatchStatus batchStatus,
        int recordsTotal,
        int recordsChecked,
        int recordsMatched,
        int recordsMismatched,
        int recordsUnverifiable,
        Instant checkedAt
) {
    /**
     * Batch-level verification outcome.
     */
    public enum BatchStatus {
        PENDING,
        VERIFIED_CLEAN,
        VERIFIED_WITH_DISPUTES,
        UNVERIFIABLE
    }

    public VerificationStatus {
        if (batchId == null || !batchId.matches("[a-zA-Z0-9][-a-zA-Z0-9_.]*")) {
            throw new IllegalArgumentException("batch_id must be alphanumeric (with dashes, dots, underscores), got: " + batchId);
        }
        if (batchStatus == null) {
            throw new IllegalArgumentException("batch_status must not be null");
        }
        if (recordsTotal < 0) {
            throw new IllegalArgumentException("records_total must be >= 0");
        }
        if (recordsChecked < 0 || recordsMatched < 0 || recordsMismatched < 0
                || recordsUnverifiable < 0) {
            throw new IllegalArgumentException("counts must be >= 0");
        }
        if (recordsMatched + recordsMismatched != recordsChecked) {
            throw new IllegalArgumentException(
                    "matched + mismatched must equal checked: "
                            + recordsMatched + " + " + recordsMismatched
                            + " != " + recordsChecked);
        }
        if (checkedAt == null) {
            throw new IllegalArgumentException("checked_at must not be null");
        }
    }

    /**
     * Create a VerificationStatus from a pipeline result.
     *
     * @param result    the pipeline result
     * @param checkedAt when verification was performed
     * @return the verification status
     */
    public static VerificationStatus fromPipelineResult(
            VerificationPipeline.PipelineResult result, Instant checkedAt) {

        AuditReport report = result.report();
        int unverifiable = 0;
        for (VerificationPipeline.RecordDetail d : result.details()) {
            if (d.status() == VerificationPipeline.RecordStatus.UNVERIFIABLE) {
                unverifiable++;
            }
        }

        BatchStatus status;
        if (report.recordsMatched() + report.recordsMismatched() == 0 && unverifiable > 0) {
            status = BatchStatus.UNVERIFIABLE;
        } else if (report.recordsMismatched() > 0) {
            status = BatchStatus.VERIFIED_WITH_DISPUTES;
        } else {
            status = BatchStatus.VERIFIED_CLEAN;
        }

        return new VerificationStatus(
                report.batchId(),
                status,
                report.recordsTotal(),
                report.recordsMatched() + report.recordsMismatched(),
                report.recordsMatched(),
                report.recordsMismatched(),
                unverifiable,
                checkedAt
        );
    }

    /**
     * Canonical text representation.
     */
    public String toCanonicalText() {
        return "batch_id:" + batchId + "\n"
                + "batch_status:" + batchStatus.name() + "\n"
                + "records_total:" + recordsTotal + "\n"
                + "records_checked:" + recordsChecked + "\n"
                + "records_matched:" + recordsMatched + "\n"
                + "records_mismatched:" + recordsMismatched + "\n"
                + "records_unverifiable:" + recordsUnverifiable + "\n"
                + "checked_at:" + DateTimeFormatter.ISO_INSTANT.format(checkedAt) + "\n";
    }

    /**
     * SHA-256 of the canonical text, as lowercase hex.
     */
    public String statusHash() {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(toCanonicalText().getBytes(StandardCharsets.UTF_8));
            return MerkleTree.encodeHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new AssertionError("SHA-256 must be available", e);
        }
    }

    /**
     * Parse a verification status from canonical text lines.
     */
    public static VerificationStatus parse(List<String> lines) {
        List<String> filtered = lines.stream()
                .map(String::strip)
                .filter(s -> !s.isEmpty())
                .toList();
        if (filtered.size() != 8) {
            throw new IllegalArgumentException(
                    "Expected 8 status lines, got " + filtered.size());
        }

        String batchId = parseField(filtered.get(0), "batch_id");
        BatchStatus status = BatchStatus.valueOf(parseField(filtered.get(1), "batch_status"));
        int total = Integer.parseInt(parseField(filtered.get(2), "records_total"));
        int checked = Integer.parseInt(parseField(filtered.get(3), "records_checked"));
        int matched = Integer.parseInt(parseField(filtered.get(4), "records_matched"));
        int mismatched = Integer.parseInt(parseField(filtered.get(5), "records_mismatched"));
        int unverifiable = Integer.parseInt(parseField(filtered.get(6), "records_unverifiable"));
        Instant checkedAt = Instant.parse(parseField(filtered.get(7), "checked_at"));

        return new VerificationStatus(batchId, status, total, checked, matched,
                mismatched, unverifiable, checkedAt);
    }

    /**
     * Save to a verification directory.
     *
     * @param verificationDir the directory to save status files in
     * @throws IOException if writing fails
     */
    public void save(Path verificationDir) throws IOException {
        Files.createDirectories(verificationDir);
        Files.writeString(
                verificationDir.resolve(batchId + ".txt"),
                toCanonicalText(),
                StandardCharsets.UTF_8);
    }

    /**
     * Load a status from the verification directory.
     *
     * @param verificationDir the directory containing status files
     * @param batchId         the batch to load status for
     * @return the status, or null if not found
     * @throws IOException if reading fails
     */
    public static VerificationStatus load(Path verificationDir, String batchId)
            throws IOException {
        Path file = verificationDir.resolve(batchId + ".txt");
        if (!Files.exists(file)) {
            return null;
        }
        List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
        return parse(lines);
    }

    /**
     * Format as human-readable summary.
     */
    public String formatReport() {
        return "Verification Status: " + batchId + "\n"
                + "  Status:        " + batchStatus + "\n"
                + "  Total records: " + recordsTotal + "\n"
                + "  Checked:       " + recordsChecked + "\n"
                + "  Matched:       " + recordsMatched + "\n"
                + "  Mismatched:    " + recordsMismatched + "\n"
                + "  Unverifiable:  " + recordsUnverifiable + "\n"
                + "  Checked at:    " + DateTimeFormatter.ISO_INSTANT.format(checkedAt) + "\n";
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
