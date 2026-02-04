package io.truthcrawl.core;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RecordComparatorTest {

    private static final Instant T1 = Instant.parse("2024-01-15T12:00:00Z");
    private static final Instant T2 = Instant.parse("2024-01-15T13:00:00Z");
    private static final String CONTENT_HASH =
            "b94d27b9934d3e08a52e52d7da7dabfac484efe37a5380ee9088f7ace2efcde9";
    private static final String NODE_ID =
            "0000000000000000000000000000000000000000000000000000000000000000";

    private ObservationRecord base(Instant t) {
        return ObservationRecord.builder()
                .version("0.1")
                .observedAt(t)
                .url("https://example.com")
                .finalUrl("https://example.com/")
                .statusCode(200)
                .fetchMs(100)
                .contentHash(CONTENT_HASH)
                .directiveCanonical("https://example.com/")
                .directiveRobotsMeta("index,follow")
                .link("https://example.com/about")
                .nodeId(NODE_ID)
                .build();
    }

    @Test
    void identical_records_match() {
        RecordComparator.Result result = RecordComparator.compare(base(T1), base(T2));
        assertTrue(result.match());
        assertTrue(result.discrepancies().isEmpty());
    }

    @Test
    void different_status_code_detected() {
        ObservationRecord actual = ObservationRecord.builder()
                .version("0.1").observedAt(T2).url("https://example.com")
                .finalUrl("https://example.com/")
                .statusCode(404).fetchMs(100).contentHash(CONTENT_HASH)
                .directiveCanonical("https://example.com/")
                .directiveRobotsMeta("index,follow")
                .link("https://example.com/about")
                .nodeId(NODE_ID).build();

        RecordComparator.Result result = RecordComparator.compare(base(T1), actual);
        assertFalse(result.match());
        assertEquals(1, result.discrepancies().size());
        assertEquals("status_code", result.discrepancies().get(0).field());
    }

    @Test
    void different_content_hash_detected() {
        ObservationRecord actual = ObservationRecord.builder()
                .version("0.1").observedAt(T2).url("https://example.com")
                .finalUrl("https://example.com/")
                .statusCode(200).fetchMs(100)
                .contentHash("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa")
                .directiveCanonical("https://example.com/")
                .directiveRobotsMeta("index,follow")
                .link("https://example.com/about")
                .nodeId(NODE_ID).build();

        RecordComparator.Result result = RecordComparator.compare(base(T1), actual);
        assertFalse(result.match());
        assertTrue(result.discrepancies().stream().anyMatch(d -> d.field().equals("content_hash")));
    }

    @Test
    void different_observed_at_and_fetch_ms_are_ignored() {
        ObservationRecord actual = ObservationRecord.builder()
                .version("0.1").observedAt(T2).url("https://example.com")
                .finalUrl("https://example.com/")
                .statusCode(200).fetchMs(999).contentHash(CONTENT_HASH)
                .directiveCanonical("https://example.com/")
                .directiveRobotsMeta("index,follow")
                .link("https://example.com/about")
                .nodeId(NODE_ID).build();

        RecordComparator.Result result = RecordComparator.compare(base(T1), actual);
        assertTrue(result.match());
    }

    @Test
    void multiple_discrepancies_all_reported() {
        ObservationRecord actual = ObservationRecord.builder()
                .version("0.1").observedAt(T2).url("https://example.com")
                .finalUrl("https://other.com/")
                .statusCode(301).fetchMs(100)
                .contentHash("bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb")
                .nodeId(NODE_ID).build();

        RecordComparator.Result result = RecordComparator.compare(base(T1), actual);
        assertFalse(result.match());
        assertTrue(result.discrepancies().size() >= 3);
    }

    @Test
    void format_report_shows_match() {
        RecordComparator.Result result = RecordComparator.compare(base(T1), base(T2));
        assertEquals("MATCH\n", RecordComparator.formatReport(result));
    }

    @Test
    void format_report_shows_mismatch_details() {
        ObservationRecord actual = ObservationRecord.builder()
                .version("0.1").observedAt(T2).url("https://example.com")
                .finalUrl("https://example.com/")
                .statusCode(404).fetchMs(100).contentHash(CONTENT_HASH)
                .directiveCanonical("https://example.com/")
                .directiveRobotsMeta("index,follow")
                .link("https://example.com/about")
                .nodeId(NODE_ID).build();

        String report = RecordComparator.formatReport(RecordComparator.compare(base(T1), actual));
        assertTrue(report.startsWith("MISMATCH\n"));
        assertTrue(report.contains("status_code"));
        assertTrue(report.contains("200"));
        assertTrue(report.contains("404"));
    }
}
