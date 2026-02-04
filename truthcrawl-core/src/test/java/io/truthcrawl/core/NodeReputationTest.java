package io.truthcrawl.core;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NodeReputationTest {

    private static final Instant T1 = Instant.parse("2024-01-15T12:00:00Z");
    private static final Instant RESOLVE_TIME = Instant.parse("2024-01-16T10:00:00Z");
    private static final String CONTENT_HASH =
            "b94d27b9934d3e08a52e52d7da7dabfac484efe37a5380ee9088f7ace2efcde9";
    private static final String ALT_CONTENT_HASH =
            "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";
    private static final String NODE_A = "a" + "0".repeat(63);
    private static final String NODE_B = "b" + "0".repeat(63);
    private static final String NODE_C = "c" + "0".repeat(63);
    private static final String NODE_D = "d" + "0".repeat(63);

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

    private DisputeResolver.Resolution resolveDispute(
            ObservationRecord challenged, List<ObservationRecord> allObs) {
        ObservationSet obsSet = ObservationSet.of(allObs);
        DisputeRecord dispute = new DisputeRecord(
                "2024-01-16-0001", challenged.recordHash(),
                allObs.get(1).recordHash(),
                "https://example.com", RESOLVE_TIME, allObs.get(1).nodeId(), null);
        return DisputeResolver.resolve(dispute, obsSet, RESOLVE_TIME);
    }

    @Test
    void majority_nodes_win_minority_nodes_lose() {
        ObservationRecord challenged = observation(NODE_A, 404, ALT_CONTENT_HASH);
        ObservationRecord obs2 = observation(NODE_B, 200, CONTENT_HASH);
        ObservationRecord obs3 = observation(NODE_C, 200, CONTENT_HASH);

        DisputeResolver.Resolution r = resolveDispute(
                challenged, List.of(challenged, obs2, obs3));

        Map<String, NodeReputation.Stats> rep = NodeReputation.compute(
                List.of(r), Map.of());

        // NODE_B and NODE_C were in majority → 1 win each
        assertEquals(1, rep.get(NODE_B).disputesWon());
        assertEquals(0, rep.get(NODE_B).disputesLost());
        assertEquals(1, rep.get(NODE_C).disputesWon());
        assertEquals(0, rep.get(NODE_C).disputesLost());

        // NODE_A was in minority → 1 loss
        assertEquals(0, rep.get(NODE_A).disputesWon());
        assertEquals(1, rep.get(NODE_A).disputesLost());
    }

    @Test
    void inconclusive_does_not_affect_reputation() {
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

        Map<String, NodeReputation.Stats> rep = NodeReputation.compute(
                List.of(r), Map.of());

        assertTrue(rep.isEmpty());
    }

    @Test
    void multiple_resolutions_accumulate() {
        // Resolution 1: NODE_A loses
        ObservationRecord challenged1 = observation(NODE_A, 404, ALT_CONTENT_HASH);
        ObservationRecord obs1b = observation(NODE_B, 200, CONTENT_HASH);
        ObservationRecord obs1c = observation(NODE_C, 200, CONTENT_HASH);
        DisputeResolver.Resolution r1 = resolveDispute(
                challenged1, List.of(challenged1, obs1b, obs1c));

        // Resolution 2: NODE_A loses again (different observations)
        ObservationRecord challenged2 = ObservationRecord.builder()
                .version("0.1").observedAt(Instant.parse("2024-01-15T14:00:00Z"))
                .url("https://example.com").finalUrl("https://example.com/")
                .statusCode(500).fetchMs(100).contentHash(ALT_CONTENT_HASH)
                .nodeId(NODE_A).build();
        ObservationRecord obs2b = ObservationRecord.builder()
                .version("0.1").observedAt(Instant.parse("2024-01-15T14:00:00Z"))
                .url("https://example.com").finalUrl("https://example.com/")
                .statusCode(200).fetchMs(100).contentHash(CONTENT_HASH)
                .nodeId(NODE_B).build();
        ObservationRecord obs2c = ObservationRecord.builder()
                .version("0.1").observedAt(Instant.parse("2024-01-15T14:00:00Z"))
                .url("https://example.com").finalUrl("https://example.com/")
                .statusCode(200).fetchMs(100).contentHash(CONTENT_HASH)
                .nodeId(NODE_C).build();

        ObservationSet obsSet2 = ObservationSet.of(List.of(challenged2, obs2b, obs2c));
        DisputeRecord dispute2 = new DisputeRecord(
                "2024-01-16-0002", challenged2.recordHash(), obs2b.recordHash(),
                "https://example.com", RESOLVE_TIME, NODE_B, null);
        DisputeResolver.Resolution r2 = DisputeResolver.resolve(dispute2, obsSet2, RESOLVE_TIME);

        Map<String, NodeReputation.Stats> rep = NodeReputation.compute(
                List.of(r1, r2), Map.of());

        assertEquals(0, rep.get(NODE_A).disputesWon());
        assertEquals(2, rep.get(NODE_A).disputesLost());
        assertEquals(2, rep.get(NODE_B).disputesWon());
        assertEquals(0, rep.get(NODE_B).disputesLost());
    }

    @Test
    void observations_total_from_counts() {
        ObservationRecord challenged = observation(NODE_A, 404, ALT_CONTENT_HASH);
        ObservationRecord obs2 = observation(NODE_B, 200, CONTENT_HASH);
        ObservationRecord obs3 = observation(NODE_C, 200, CONTENT_HASH);

        DisputeResolver.Resolution r = resolveDispute(
                challenged, List.of(challenged, obs2, obs3));

        Map<String, Integer> obsCounts = Map.of(
                NODE_A, 50, NODE_B, 100, NODE_C, 75);

        Map<String, NodeReputation.Stats> rep = NodeReputation.compute(
                List.of(r), obsCounts);

        assertEquals(50, rep.get(NODE_A).observationsTotal());
        assertEquals(100, rep.get(NODE_B).observationsTotal());
        assertEquals(75, rep.get(NODE_C).observationsTotal());
    }

    @Test
    void format_report_shows_all_nodes() {
        ObservationRecord challenged = observation(NODE_A, 404, ALT_CONTENT_HASH);
        ObservationRecord obs2 = observation(NODE_B, 200, CONTENT_HASH);
        ObservationRecord obs3 = observation(NODE_C, 200, CONTENT_HASH);

        DisputeResolver.Resolution r = resolveDispute(
                challenged, List.of(challenged, obs2, obs3));

        Map<String, NodeReputation.Stats> rep = NodeReputation.compute(
                List.of(r), Map.of());

        String report = NodeReputation.formatReport(rep);
        assertTrue(report.contains("node:" + NODE_A));
        assertTrue(report.contains("node:" + NODE_B));
        assertTrue(report.contains("disputes_won:"));
        assertTrue(report.contains("disputes_lost:"));
    }

    @Test
    void deterministic_same_inputs_same_output() {
        ObservationRecord challenged = observation(NODE_A, 404, ALT_CONTENT_HASH);
        ObservationRecord obs2 = observation(NODE_B, 200, CONTENT_HASH);
        ObservationRecord obs3 = observation(NODE_C, 200, CONTENT_HASH);

        DisputeResolver.Resolution r = resolveDispute(
                challenged, List.of(challenged, obs2, obs3));

        Map<String, NodeReputation.Stats> rep1 = NodeReputation.compute(
                List.of(r), Map.of(NODE_A, 10));
        Map<String, NodeReputation.Stats> rep2 = NodeReputation.compute(
                List.of(r), Map.of(NODE_A, 10));

        assertEquals(rep1.keySet(), rep2.keySet());
        for (String nodeId : rep1.keySet()) {
            assertEquals(rep1.get(nodeId).disputesWon(), rep2.get(nodeId).disputesWon());
            assertEquals(rep1.get(nodeId).disputesLost(), rep2.get(nodeId).disputesLost());
            assertEquals(rep1.get(nodeId).observationsTotal(), rep2.get(nodeId).observationsTotal());
        }
    }
}
