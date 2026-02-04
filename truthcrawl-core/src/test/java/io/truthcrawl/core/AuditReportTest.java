package io.truthcrawl.core;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AuditReportTest {

    @Test
    void canonical_text_has_fixed_field_order() {
        AuditReport report = new AuditReport("2024-01-15", 100, 10, 9, 1, 1);
        String text = report.toCanonicalText();
        String[] lines = text.split("\n");
        assertEquals(6, lines.length);
        assertTrue(lines[0].startsWith("batch_id:"));
        assertTrue(lines[1].startsWith("records_total:"));
        assertTrue(lines[2].startsWith("records_sampled:"));
        assertTrue(lines[3].startsWith("records_matched:"));
        assertTrue(lines[4].startsWith("records_mismatched:"));
        assertTrue(lines[5].startsWith("disputes_filed:"));
    }

    @Test
    void report_hash_is_deterministic() {
        AuditReport r1 = new AuditReport("2024-01-15", 100, 10, 9, 1, 1);
        AuditReport r2 = new AuditReport("2024-01-15", 100, 10, 9, 1, 1);
        assertEquals(r1.reportHash(), r2.reportHash());
        assertEquals(64, r1.reportHash().length());
    }

    @Test
    void parse_round_trip() {
        AuditReport original = new AuditReport("2024-01-15", 100, 10, 8, 2, 1);
        AuditReport parsed = AuditReport.parse(
                List.of(original.toCanonicalText().split("\n")));

        assertEquals(original.batchId(), parsed.batchId());
        assertEquals(original.recordsTotal(), parsed.recordsTotal());
        assertEquals(original.recordsSampled(), parsed.recordsSampled());
        assertEquals(original.recordsMatched(), parsed.recordsMatched());
        assertEquals(original.recordsMismatched(), parsed.recordsMismatched());
        assertEquals(original.disputesFiled(), parsed.disputesFiled());
    }

    @Test
    void clean_audit_report() {
        AuditReport report = new AuditReport("2024-01-15", 50, 10, 10, 0, 0);
        assertEquals(0, report.recordsMismatched());
        assertEquals(0, report.disputesFiled());
    }

    @Test
    void format_report_shows_summary() {
        AuditReport report = new AuditReport("2024-01-15", 100, 10, 9, 1, 1);
        String formatted = report.formatReport();
        assertTrue(formatted.contains("2024-01-15"));
        assertTrue(formatted.contains("100"));
        assertTrue(formatted.contains("Mismatched"));
    }

    @Test
    void rejects_invalid_batch_id() {
        assertThrows(IllegalArgumentException.class, () ->
                new AuditReport("-invalid", 100, 10, 9, 1, 1));
    }

    @Test
    void rejects_sampled_exceeding_total() {
        assertThrows(IllegalArgumentException.class, () ->
                new AuditReport("2024-01-15", 10, 20, 15, 5, 0));
    }

    @Test
    void rejects_matched_plus_mismatched_not_equaling_sampled() {
        assertThrows(IllegalArgumentException.class, () ->
                new AuditReport("2024-01-15", 100, 10, 5, 3, 0));
    }

    @Test
    void rejects_disputes_exceeding_mismatched() {
        assertThrows(IllegalArgumentException.class, () ->
                new AuditReport("2024-01-15", 100, 10, 8, 2, 5));
    }
}
