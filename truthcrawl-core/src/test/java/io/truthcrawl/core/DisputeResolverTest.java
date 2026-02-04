package io.truthcrawl.core;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DisputeResolverTest {

    private static final Instant T1 = Instant.parse("2024-01-15T12:00:00Z");
    private static final Instant T2 = Instant.parse("2024-01-15T13:00:00Z");
    private static final Instant RESOLVE_TIME = Instant.parse("2024-01-16T10:00:00Z");
    private static final String CONTENT_HASH =
            "b94d27b9934d3e08a52e52d7da7dabfac484efe37a5380ee9088f7ace2efcde9";
    private static final String ALT_CONTENT_HASH =
            "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";
    private static final String NODE_A = "a" + "0".repeat(63);
    private static final String NODE_B = "b" + "0".repeat(63);
    private static final String NODE_C = "c" + "0".repeat(63);
    private static final String NODE_D = "d" + "0".repeat(63);
    private static final String NODE_E = "e" + "0".repeat(63);

    private ObservationRecord observation(String nodeId, int statusCode, String contentHash) {
        return ObservationRecord.builder()
                .version("0.1")
                .observedAt(T1)
                .url("https://example.com")
                .finalUrl("https://example.com/")
                .statusCode(statusCode)
                .fetchMs(100)
                .contentHash(contentHash)
                .nodeId(nodeId)
                .build();
    }

    private ObservationRecord observation(String nodeId) {
        return observation(nodeId, 200, CONTENT_HASH);
    }

    /**
     * DISMISSED: 3/3 nodes agree on all fields, including the challenged observation.
     * The challenger was wrong — dispute is dismissed.
     */
    @Test
    void dismissed_when_challenged_matches_majority() {
        ObservationRecord challenged = observation(NODE_A);
        ObservationRecord obs2 = observation(NODE_B);
        ObservationRecord obs3 = observation(NODE_C);

        ObservationSet obsSet = ObservationSet.of(List.of(challenged, obs2, obs3));
        DisputeRecord dispute = new DisputeRecord(
                "2024-01-16-0001", challenged.recordHash(), obs2.recordHash(),
                "https://example.com", RESOLVE_TIME, NODE_B, null);

        DisputeResolver.Resolution r = DisputeResolver.resolve(dispute, obsSet, RESOLVE_TIME);

        assertEquals(DisputeResolver.Outcome.DISMISSED, r.outcome());
        assertEquals(3, r.majorityNodeIds().size());
        assertTrue(r.minorityNodeIds().isEmpty());
    }

    /**
     * UPHELD: challenged observation has status_code=404, majority say 200.
     * The original was wrong — dispute is upheld.
     */
    @Test
    void upheld_when_challenged_differs_from_majority() {
        ObservationRecord challenged = observation(NODE_A, 404, ALT_CONTENT_HASH);
        ObservationRecord obs2 = observation(NODE_B, 200, CONTENT_HASH);
        ObservationRecord obs3 = observation(NODE_C, 200, CONTENT_HASH);

        ObservationSet obsSet = ObservationSet.of(List.of(challenged, obs2, obs3));
        DisputeRecord dispute = new DisputeRecord(
                "2024-01-16-0001", challenged.recordHash(), obs2.recordHash(),
                "https://example.com", RESOLVE_TIME, NODE_B, null);

        DisputeResolver.Resolution r = DisputeResolver.resolve(dispute, obsSet, RESOLVE_TIME);

        assertEquals(DisputeResolver.Outcome.UPHELD, r.outcome());
        assertTrue(r.majorityNodeIds().contains(NODE_B));
        assertTrue(r.majorityNodeIds().contains(NODE_C));
        assertTrue(r.minorityNodeIds().contains(NODE_A));
    }

    /**
     * INCONCLUSIVE: no majority on status_code (2 say 200, 2 say 404).
     */
    @Test
    void inconclusive_when_no_majority() {
        ObservationRecord obs1 = observation(NODE_A, 200, CONTENT_HASH);
        ObservationRecord obs2 = observation(NODE_B, 200, CONTENT_HASH);
        ObservationRecord obs3 = observation(NODE_C, 404, ALT_CONTENT_HASH);
        ObservationRecord obs4 = observation(NODE_D, 404, ALT_CONTENT_HASH);

        ObservationSet obsSet = ObservationSet.of(List.of(obs1, obs2, obs3, obs4));
        DisputeRecord dispute = new DisputeRecord(
                "2024-01-16-0001", obs1.recordHash(), obs3.recordHash(),
                "https://example.com", RESOLVE_TIME, NODE_C, null);

        DisputeResolver.Resolution r = DisputeResolver.resolve(dispute, obsSet, RESOLVE_TIME);

        assertEquals(DisputeResolver.Outcome.INCONCLUSIVE, r.outcome());
    }

    /**
     * Order-independence: same observations in different order produce same outcome.
     */
    @Test
    void resolution_is_order_independent() {
        ObservationRecord challenged = observation(NODE_A, 404, ALT_CONTENT_HASH);
        ObservationRecord obs2 = observation(NODE_B, 200, CONTENT_HASH);
        ObservationRecord obs3 = observation(NODE_C, 200, CONTENT_HASH);

        DisputeRecord dispute = new DisputeRecord(
                "2024-01-16-0001", challenged.recordHash(), obs2.recordHash(),
                "https://example.com", RESOLVE_TIME, NODE_B, null);

        // Order 1: A, B, C
        ObservationSet set1 = ObservationSet.of(List.of(challenged, obs2, obs3));
        DisputeResolver.Resolution r1 = DisputeResolver.resolve(dispute, set1, RESOLVE_TIME);

        // Order 2: C, A, B
        ObservationSet set2 = ObservationSet.of(List.of(obs3, challenged, obs2));
        DisputeResolver.Resolution r2 = DisputeResolver.resolve(dispute, set2, RESOLVE_TIME);

        assertEquals(r1.outcome(), r2.outcome());
        assertEquals(r1.majorityNodeIds(), r2.majorityNodeIds());
        assertEquals(r1.minorityNodeIds(), r2.minorityNodeIds());
        assertEquals(r1.toCanonicalText(), r2.toCanonicalText());
    }

    /**
     * 5-node resolution: 3 vs 2 majority.
     */
    @Test
    void five_node_majority_wins() {
        ObservationRecord challenged = observation(NODE_A, 404, ALT_CONTENT_HASH);
        ObservationRecord obs2 = observation(NODE_B, 200, CONTENT_HASH);
        ObservationRecord obs3 = observation(NODE_C, 200, CONTENT_HASH);
        ObservationRecord obs4 = observation(NODE_D, 200, CONTENT_HASH);
        ObservationRecord obs5 = observation(NODE_E, 404, ALT_CONTENT_HASH);

        ObservationSet obsSet = ObservationSet.of(List.of(challenged, obs2, obs3, obs4, obs5));
        DisputeRecord dispute = new DisputeRecord(
                "2024-01-16-0001", challenged.recordHash(), obs2.recordHash(),
                "https://example.com", RESOLVE_TIME, NODE_B, null);

        DisputeResolver.Resolution r = DisputeResolver.resolve(dispute, obsSet, RESOLVE_TIME);

        assertEquals(DisputeResolver.Outcome.UPHELD, r.outcome());
        assertEquals(3, r.majorityNodeIds().size());
        assertEquals(2, r.minorityNodeIds().size());
    }

    @Test
    void rejects_fewer_than_3_observations() {
        ObservationRecord obs1 = observation(NODE_A);
        ObservationRecord obs2 = observation(NODE_B);

        ObservationSet obsSet = ObservationSet.of(List.of(obs1, obs2));
        DisputeRecord dispute = new DisputeRecord(
                "2024-01-16-0001", obs1.recordHash(), obs2.recordHash(),
                "https://example.com", RESOLVE_TIME, NODE_B, null);

        assertThrows(IllegalArgumentException.class, () ->
                DisputeResolver.resolve(dispute, obsSet, RESOLVE_TIME));
    }

    @Test
    void rejects_challenged_record_not_in_set() {
        ObservationRecord obs1 = observation(NODE_A);
        ObservationRecord obs2 = observation(NODE_B);
        ObservationRecord obs3 = observation(NODE_C);

        ObservationSet obsSet = ObservationSet.of(List.of(obs1, obs2, obs3));

        // Use a hash that doesn't match any observation
        DisputeRecord dispute = new DisputeRecord(
                "2024-01-16-0001",
                "fffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff0",
                obs2.recordHash(),
                "https://example.com", RESOLVE_TIME, NODE_B, null);

        assertThrows(IllegalArgumentException.class, () ->
                DisputeResolver.resolve(dispute, obsSet, RESOLVE_TIME));
    }

    @Test
    void resolution_canonical_text_is_deterministic() {
        ObservationRecord challenged = observation(NODE_A, 404, ALT_CONTENT_HASH);
        ObservationRecord obs2 = observation(NODE_B, 200, CONTENT_HASH);
        ObservationRecord obs3 = observation(NODE_C, 200, CONTENT_HASH);

        ObservationSet obsSet = ObservationSet.of(List.of(challenged, obs2, obs3));
        DisputeRecord dispute = new DisputeRecord(
                "2024-01-16-0001", challenged.recordHash(), obs2.recordHash(),
                "https://example.com", RESOLVE_TIME, NODE_B, null);

        DisputeResolver.Resolution r1 = DisputeResolver.resolve(dispute, obsSet, RESOLVE_TIME);
        DisputeResolver.Resolution r2 = DisputeResolver.resolve(dispute, obsSet, RESOLVE_TIME);

        assertEquals(r1.toCanonicalText(), r2.toCanonicalText());
        assertEquals(r1.resolutionHash(), r2.resolutionHash());
    }

    @Test
    void resolution_hash_is_64_char_hex() {
        ObservationRecord obs1 = observation(NODE_A);
        ObservationRecord obs2 = observation(NODE_B);
        ObservationRecord obs3 = observation(NODE_C);

        ObservationSet obsSet = ObservationSet.of(List.of(obs1, obs2, obs3));
        DisputeRecord dispute = new DisputeRecord(
                "2024-01-16-0001", obs1.recordHash(), obs2.recordHash(),
                "https://example.com", RESOLVE_TIME, NODE_B, null);

        DisputeResolver.Resolution r = DisputeResolver.resolve(dispute, obsSet, RESOLVE_TIME);

        assertEquals(64, r.resolutionHash().length());
        assertTrue(r.resolutionHash().matches("[0-9a-f]{64}"));
    }

    @Test
    void resolution_parse_round_trip() {
        ObservationRecord challenged = observation(NODE_A, 404, ALT_CONTENT_HASH);
        ObservationRecord obs2 = observation(NODE_B, 200, CONTENT_HASH);
        ObservationRecord obs3 = observation(NODE_C, 200, CONTENT_HASH);

        ObservationSet obsSet = ObservationSet.of(List.of(challenged, obs2, obs3));
        DisputeRecord dispute = new DisputeRecord(
                "2024-01-16-0001", challenged.recordHash(), obs2.recordHash(),
                "https://example.com", RESOLVE_TIME, NODE_B, null);

        DisputeResolver.Resolution original = DisputeResolver.resolve(dispute, obsSet, RESOLVE_TIME);
        String text = original.toCanonicalText();

        DisputeResolver.Resolution parsed = DisputeResolver.Resolution.parse(
                List.of(text.split("\n")));

        assertEquals(original.disputeId(), parsed.disputeId());
        assertEquals(original.outcome(), parsed.outcome());
        assertEquals(original.resolvedAt(), parsed.resolvedAt());
        assertEquals(original.observationsCount(), parsed.observationsCount());
        assertEquals(original.majorityNodeIds(), parsed.majorityNodeIds());
        assertEquals(original.minorityNodeIds(), parsed.minorityNodeIds());
        assertEquals(original.fieldResults().size(), parsed.fieldResults().size());
    }

    @Test
    void format_report_shows_outcome() {
        ObservationRecord challenged = observation(NODE_A, 404, ALT_CONTENT_HASH);
        ObservationRecord obs2 = observation(NODE_B, 200, CONTENT_HASH);
        ObservationRecord obs3 = observation(NODE_C, 200, CONTENT_HASH);

        ObservationSet obsSet = ObservationSet.of(List.of(challenged, obs2, obs3));
        DisputeRecord dispute = new DisputeRecord(
                "2024-01-16-0001", challenged.recordHash(), obs2.recordHash(),
                "https://example.com", RESOLVE_TIME, NODE_B, null);

        DisputeResolver.Resolution r = DisputeResolver.resolve(dispute, obsSet, RESOLVE_TIME);
        String report = DisputeResolver.formatReport(r);

        assertTrue(report.startsWith("UPHELD\n"));
        assertTrue(report.contains("status_code"));
        assertTrue(report.contains("majority:"));
    }

    @Test
    void field_consensus_reports_all_consensus_fields() {
        ObservationRecord obs1 = observation(NODE_A);
        ObservationRecord obs2 = observation(NODE_B);
        ObservationRecord obs3 = observation(NODE_C);

        ObservationSet obsSet = ObservationSet.of(List.of(obs1, obs2, obs3));
        DisputeRecord dispute = new DisputeRecord(
                "2024-01-16-0001", obs1.recordHash(), obs2.recordHash(),
                "https://example.com", RESOLVE_TIME, NODE_B, null);

        DisputeResolver.Resolution r = DisputeResolver.resolve(dispute, obsSet, RESOLVE_TIME);

        assertEquals(6, r.fieldResults().size());
        assertEquals("status_code", r.fieldResults().get(0).field());
        assertEquals("content_hash", r.fieldResults().get(1).field());
        assertEquals("final_url", r.fieldResults().get(2).field());
        assertEquals("directive:canonical", r.fieldResults().get(3).field());
        assertEquals("directive:robots_meta", r.fieldResults().get(4).field());
        assertEquals("directive:robots_header", r.fieldResults().get(5).field());
    }
}
