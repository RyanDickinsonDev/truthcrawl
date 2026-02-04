package io.truthcrawl.core;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Computes per-node reputation from published dispute resolutions.
 *
 * <p>Reputation rules:
 * <ul>
 *   <li>disputes_won: times the node was in the majority of a resolved dispute</li>
 *   <li>disputes_lost: times the node was in the minority of a resolved dispute</li>
 *   <li>observations_total: total observations published by the node</li>
 *   <li>INCONCLUSIVE outcomes do not affect win/loss counts</li>
 *   <li>Reputation is deterministic: same inputs produce same scores</li>
 *   <li>Reputation is computed from published data only (no hidden state)</li>
 * </ul>
 */
public final class NodeReputation {

    private NodeReputation() {}

    /**
     * Per-node reputation statistics.
     *
     * @param nodeId            the node's identifier
     * @param disputesWon       disputes where the node was in the majority
     * @param disputesLost      disputes where the node was in the minority
     * @param observationsTotal total observations published by this node
     */
    public record Stats(
            String nodeId,
            int disputesWon,
            int disputesLost,
            int observationsTotal
    ) {}

    /**
     * Compute reputation from resolutions and observation counts.
     *
     * <p>Only UPHELD and DISMISSED resolutions affect win/loss counts.
     * INCONCLUSIVE resolutions are ignored (no win or loss).
     *
     * @param resolutions       list of dispute resolutions
     * @param observationCounts per-node observation counts (node_id → count)
     * @return sorted map of node_id → Stats
     */
    public static Map<String, Stats> compute(
            List<DisputeResolver.Resolution> resolutions,
            Map<String, Integer> observationCounts) {

        Map<String, int[]> tally = new LinkedHashMap<>(); // [won, lost]

        for (DisputeResolver.Resolution r : resolutions) {
            if (r.outcome() == DisputeResolver.Outcome.INCONCLUSIVE) {
                continue;
            }
            for (String nodeId : r.majorityNodeIds()) {
                tally.computeIfAbsent(nodeId, k -> new int[2])[0]++;
            }
            for (String nodeId : r.minorityNodeIds()) {
                tally.computeIfAbsent(nodeId, k -> new int[2])[1]++;
            }
        }

        // Merge all known node IDs (sorted for determinism)
        Set<String> allNodes = new TreeSet<>();
        allNodes.addAll(tally.keySet());
        allNodes.addAll(observationCounts.keySet());

        Map<String, Stats> result = new LinkedHashMap<>();
        for (String nodeId : allNodes) {
            int[] t = tally.getOrDefault(nodeId, new int[2]);
            int obs = observationCounts.getOrDefault(nodeId, 0);
            result.put(nodeId, new Stats(nodeId, t[0], t[1], obs));
        }

        return Collections.unmodifiableMap(result);
    }

    /**
     * Format reputation statistics as human-readable text.
     */
    public static String formatReport(Map<String, Stats> reputations) {
        StringBuilder sb = new StringBuilder();
        for (Stats s : reputations.values()) {
            sb.append("node:").append(s.nodeId()).append('\n');
            sb.append("  disputes_won:").append(s.disputesWon()).append('\n');
            sb.append("  disputes_lost:").append(s.disputesLost()).append('\n');
            sb.append("  observations_total:").append(s.observationsTotal()).append('\n');
        }
        return sb.toString();
    }
}
