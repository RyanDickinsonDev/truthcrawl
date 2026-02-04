package io.truthcrawl.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Compares two ObservationRecords for the same URL and reports field-level discrepancies.
 *
 * <p>Used for verification sampling: a verifier re-fetches a URL and compares
 * the new observation against the originally published one.
 */
public final class RecordComparator {

    private RecordComparator() {}

    /**
     * A single field-level discrepancy between two records.
     *
     * @param field    field name
     * @param expected value from the original record
     * @param actual   value from the re-fetched record
     */
    public record Discrepancy(String field, String expected, String actual) {}

    /**
     * Comparison result.
     *
     * @param match         true if all compared fields match
     * @param discrepancies list of field-level differences (empty if match)
     */
    public record Result(boolean match, List<Discrepancy> discrepancies) {
        public static Result ok() {
            return new Result(true, List.of());
        }

        public static Result mismatch(List<Discrepancy> discrepancies) {
            return new Result(false, Collections.unmodifiableList(discrepancies));
        }
    }

    /**
     * Compare two records. The original is the published record; the actual is the re-fetch.
     *
     * <p>Fields compared: status_code, content_hash, final_url, directives, outbound_links.
     * Fields NOT compared: observed_at, fetch_ms, node_id, node_signature (these are expected to differ).
     *
     * @param original the published record
     * @param actual   the re-fetched record
     * @return comparison result
     */
    public static Result compare(ObservationRecord original, ObservationRecord actual) {
        List<Discrepancy> diffs = new ArrayList<>();

        compareField(diffs, "status_code",
                String.valueOf(original.statusCode()), String.valueOf(actual.statusCode()));
        compareField(diffs, "content_hash",
                original.contentHash(), actual.contentHash());
        compareField(diffs, "final_url",
                original.finalUrl(), actual.finalUrl());
        compareField(diffs, "directive:canonical",
                nullSafe(original.directiveCanonical()), nullSafe(actual.directiveCanonical()));
        compareField(diffs, "directive:robots_meta",
                nullSafe(original.directiveRobotsMeta()), nullSafe(actual.directiveRobotsMeta()));
        compareField(diffs, "directive:robots_header",
                nullSafe(original.directiveRobotsHeader()), nullSafe(actual.directiveRobotsHeader()));
        compareField(diffs, "outbound_links",
                original.outboundLinks().toString(), actual.outboundLinks().toString());

        if (diffs.isEmpty()) {
            return Result.ok();
        }
        return Result.mismatch(diffs);
    }

    /**
     * Format discrepancies as human-readable text.
     */
    public static String formatReport(Result result) {
        if (result.match()) {
            return "MATCH\n";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("MISMATCH\n");
        for (Discrepancy d : result.discrepancies()) {
            sb.append("  ").append(d.field()).append(":\n");
            sb.append("    expected: ").append(d.expected()).append("\n");
            sb.append("    actual:   ").append(d.actual()).append("\n");
        }
        return sb.toString();
    }

    private static void compareField(List<Discrepancy> diffs, String field,
                                      String expected, String actual) {
        if (!Objects.equals(expected, actual)) {
            diffs.add(new Discrepancy(field, expected, actual));
        }
    }

    private static String nullSafe(String s) {
        return s == null ? "" : s;
    }
}
