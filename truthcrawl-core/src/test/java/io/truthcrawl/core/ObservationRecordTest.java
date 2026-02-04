package io.truthcrawl.core;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ObservationRecordTest {

    private static final Instant TIMESTAMP = Instant.parse("2024-01-15T12:00:00Z");
    private static final String CONTENT_HASH =
            "b94d27b9934d3e08a52e52d7da7dabfac484efe37a5380ee9088f7ace2efcde9";
    private static final String NODE_ID = "abc123placeholder";

    // Golden vector: SHA-256 of the canonical text below (independently computed in Python)
    private static final String EXPECTED_RECORD_HASH =
            "21909b72c0453322cca982390fdc5185767b6268dfa150004c0c2ddd20cba084";

    private ObservationRecord buildTestRecord() {
        return ObservationRecord.builder()
                .version("0.1")
                .observedAt(TIMESTAMP)
                .url("https://example.com")
                .finalUrl("https://example.com/")
                .statusCode(200)
                .fetchMs(142)
                .contentHash(CONTENT_HASH)
                .header("content-type", "text/html")
                .header("server", "nginx")
                .directiveCanonical("https://example.com/")
                .directiveRobotsMeta("index,follow")
                .directiveRobotsHeader(null)
                .link("https://example.com/about")
                .link("https://example.com/contact")
                .nodeId(NODE_ID)
                .build();
    }

    @Test
    void canonical_text_has_fixed_field_order() {
        ObservationRecord record = buildTestRecord();
        String expected = "version:0.1\n"
                + "observed_at:2024-01-15T12:00:00Z\n"
                + "url:https://example.com\n"
                + "final_url:https://example.com/\n"
                + "status_code:200\n"
                + "fetch_ms:142\n"
                + "content_hash:" + CONTENT_HASH + "\n"
                + "header:content-type:text/html\n"
                + "header:server:nginx\n"
                + "directive:canonical:https://example.com/\n"
                + "directive:robots_meta:index,follow\n"
                + "directive:robots_header:\n"
                + "link:https://example.com/about\n"
                + "link:https://example.com/contact\n"
                + "node_id:" + NODE_ID + "\n";
        assertEquals(expected, record.toCanonicalText());
    }

    @Test
    void record_hash_matches_golden_vector() {
        ObservationRecord record = buildTestRecord();
        assertEquals(EXPECTED_RECORD_HASH, record.recordHash());
    }

    @Test
    void record_hash_changes_if_content_changes() {
        ObservationRecord original = buildTestRecord();
        ObservationRecord modified = ObservationRecord.builder()
                .version("0.1")
                .observedAt(TIMESTAMP)
                .url("https://example.com")
                .finalUrl("https://example.com/")
                .statusCode(404)
                .fetchMs(142)
                .contentHash(CONTENT_HASH)
                .nodeId(NODE_ID)
                .build();
        assertNotEquals(original.recordHash(), modified.recordHash());
    }

    @Test
    void null_directives_serialize_as_empty() {
        ObservationRecord record = ObservationRecord.builder()
                .version("0.1")
                .observedAt(TIMESTAMP)
                .url("https://example.com")
                .finalUrl("https://example.com/")
                .statusCode(200)
                .fetchMs(100)
                .contentHash(CONTENT_HASH)
                .nodeId(NODE_ID)
                .build();
        String text = record.toCanonicalText();
        assert text.contains("directive:canonical:\n");
        assert text.contains("directive:robots_meta:\n");
        assert text.contains("directive:robots_header:\n");
    }

    @Test
    void links_are_sorted_and_deduped() {
        ObservationRecord record = ObservationRecord.builder()
                .version("0.1")
                .observedAt(TIMESTAMP)
                .url("https://example.com")
                .finalUrl("https://example.com/")
                .statusCode(200)
                .fetchMs(100)
                .contentHash(CONTENT_HASH)
                .link("https://z.com")
                .link("https://a.com")
                .link("https://z.com")
                .nodeId(NODE_ID)
                .build();
        assertEquals(List.of("https://a.com", "https://z.com"), record.outboundLinks());
    }

    @Test
    void headers_are_sorted_by_key_lowercase() {
        ObservationRecord record = ObservationRecord.builder()
                .version("0.1")
                .observedAt(TIMESTAMP)
                .url("https://example.com")
                .finalUrl("https://example.com/")
                .statusCode(200)
                .fetchMs(100)
                .contentHash(CONTENT_HASH)
                .header("Server", "nginx")
                .header("Content-Type", "text/html")
                .nodeId(NODE_ID)
                .build();
        String text = record.toCanonicalText();
        int ctPos = text.indexOf("header:content-type:");
        int sPos = text.indexOf("header:server:");
        assert ctPos < sPos : "content-type should appear before server";
    }

    @Test
    void parse_round_trips_with_full_text() {
        ObservationRecord original = buildTestRecord().withSignature("dGVzdHNpZw==");
        List<String> lines = List.of(original.toFullText().split("\n"));
        ObservationRecord parsed = ObservationRecord.parse(lines);

        assertEquals(original.toCanonicalText(), parsed.toCanonicalText());
        assertEquals(original.nodeSignature(), parsed.nodeSignature());
        assertEquals(original.recordHash(), parsed.recordHash());
    }

    @Test
    void parse_handles_null_signature() {
        ObservationRecord original = buildTestRecord();
        List<String> lines = List.of(original.toFullText().split("\n"));
        ObservationRecord parsed = ObservationRecord.parse(lines);
        assertNull(parsed.nodeSignature());
    }

    @Test
    void with_signature_does_not_change_hash() {
        ObservationRecord unsigned = buildTestRecord();
        ObservationRecord signed = unsigned.withSignature("dGVzdHNpZw==");
        assertEquals(unsigned.recordHash(), signed.recordHash());
    }

    @Test
    void throws_on_missing_required_fields() {
        assertThrows(IllegalArgumentException.class, () ->
                ObservationRecord.builder()
                        .version("0.1")
                        .observedAt(TIMESTAMP)
                        .build());
    }
}
