package io.truthcrawl.core;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeSet;

/**
 * Resolves disputes by computing majority consensus across independent observations.
 *
 * <p>Resolution rules:
 * <ul>
 *   <li>Requires at least 3 independent observations of the same URL</li>
 *   <li>Consensus fields: status_code, content_hash, final_url, directives</li>
 *   <li>Timing fields (fetch_ms, observed_at) excluded from consensus</li>
 *   <li>Majority: &gt; 50% of observations agree on a field value</li>
 *   <li>UPHELD: challenged record disagrees with majority (original was wrong)</li>
 *   <li>DISMISSED: challenged record agrees with majority (original was correct)</li>
 *   <li>INCONCLUSIVE: no majority exists on at least one field</li>
 * </ul>
 *
 * <p>Resolution is deterministic and order-independent.
 */
public final class DisputeResolver {

    private DisputeResolver() {}

    public enum Outcome {
        UPHELD,
        DISMISSED,
        INCONCLUSIVE
    }

    /** Consensus fields compared, in canonical order. */
    static final List<String> CONSENSUS_FIELDS = List.of(
            "status_code", "content_hash", "final_url",
            "directive:canonical", "directive:robots_meta", "directive:robots_header"
    );

    /**
     * Per-field consensus result.
     *
     * @param field            field name
     * @param majorityValue    value with &gt; 50% agreement, null if no majority
     * @param challengedValue  value from the challenged record
     * @param majorityCount    number of observations with the majority value (0 if no majority)
     * @param totalCount       total observations
     */
    public record FieldConsensus(
            String field,
            String majorityValue,
            String challengedValue,
            int majorityCount,
            int totalCount
    ) {
        public boolean hasMajority() {
            return majorityValue != null;
        }

        public boolean challengedMatchesMajority() {
            return hasMajority() && Objects.equals(majorityValue, challengedValue);
        }
    }

    /**
     * Full resolution result. Contains all data needed for publishing and verification.
     *
     * <p>Canonical text format (fixed field order):
     * <pre>
     * dispute_id:2024-01-16-0001
     * outcome:UPHELD
     * resolved_at:2024-01-16T12:00:00Z
     * observations_count:5
     * field:status_code
     * majority:200
     * count:3/5
     * challenged:404
     * field:content_hash
     * majority:abc...
     * count:3/5
     * challenged:abc...
     * ...
     * majority_nodes:nodeA,nodeB,nodeC
     * minority_nodes:nodeD,nodeE
     * </pre>
     */
    public record Resolution(
            String disputeId,
            Outcome outcome,
            Instant resolvedAt,
            int observationsCount,
            List<FieldConsensus> fieldResults,
            List<String> majorityNodeIds,
            List<String> minorityNodeIds
    ) {
        /**
         * Canonical text for hashing and publishing.
         */
        public String toCanonicalText() {
            StringBuilder sb = new StringBuilder();
            sb.append("dispute_id:").append(disputeId).append('\n');
            sb.append("outcome:").append(outcome.name()).append('\n');
            sb.append("resolved_at:").append(DateTimeFormatter.ISO_INSTANT.format(resolvedAt)).append('\n');
            sb.append("observations_count:").append(observationsCount).append('\n');
            for (FieldConsensus fc : fieldResults) {
                sb.append("field:").append(fc.field()).append('\n');
                sb.append("majority:").append(fc.majorityValue() == null ? "" : fc.majorityValue()).append('\n');
                sb.append("count:").append(fc.majorityCount()).append('/').append(fc.totalCount()).append('\n');
                sb.append("challenged:").append(fc.challengedValue()).append('\n');
            }
            sb.append("majority_nodes:").append(String.join(",", majorityNodeIds)).append('\n');
            sb.append("minority_nodes:").append(String.join(",", minorityNodeIds)).append('\n');
            return sb.toString();
        }

        /**
         * SHA-256 of canonical text, as lowercase hex.
         */
        public String resolutionHash() {
            try {
                MessageDigest digest = MessageDigest.getInstance("SHA-256");
                byte[] hash = digest.digest(toCanonicalText().getBytes(StandardCharsets.UTF_8));
                return MerkleTree.encodeHex(hash);
            } catch (NoSuchAlgorithmException e) {
                throw new AssertionError("SHA-256 must be available", e);
            }
        }

        /**
         * Parse a resolution from its canonical text representation.
         */
        public static Resolution parse(List<String> lines) {
            String disputeId = null;
            Outcome outcome = null;
            Instant resolvedAt = null;
            int observationsCount = 0;
            List<FieldConsensus> fieldResults = new ArrayList<>();
            List<String> majorityNodeIds = null;
            List<String> minorityNodeIds = null;

            String currentField = null;
            String currentMajority = null;
            int currentMajorityCount = 0;
            int currentTotal = 0;

            for (String line : lines) {
                if (line.isBlank()) continue;
                int colon = line.indexOf(':');
                if (colon == -1) throw new IllegalArgumentException("Missing ':' in line: " + line);
                String key = line.substring(0, colon);
                String value = line.substring(colon + 1);
                switch (key) {
                    case "dispute_id" -> disputeId = value;
                    case "outcome" -> outcome = Outcome.valueOf(value);
                    case "resolved_at" -> resolvedAt = Instant.parse(value);
                    case "observations_count" -> observationsCount = Integer.parseInt(value);
                    case "field" -> {
                        currentField = value;
                        currentMajority = null;
                        currentMajorityCount = 0;
                        currentTotal = 0;
                    }
                    case "majority" -> currentMajority = value;
                    case "count" -> {
                        String[] parts = value.split("/");
                        currentMajorityCount = Integer.parseInt(parts[0]);
                        currentTotal = Integer.parseInt(parts[1]);
                    }
                    case "challenged" -> {
                        fieldResults.add(new FieldConsensus(
                                currentField,
                                currentMajority == null || currentMajority.isEmpty() ? null : currentMajority,
                                value, currentMajorityCount, currentTotal));
                        currentField = null;
                    }
                    case "majority_nodes" -> majorityNodeIds = value.isEmpty()
                            ? List.of() : List.of(value.split(","));
                    case "minority_nodes" -> minorityNodeIds = value.isEmpty()
                            ? List.of() : List.of(value.split(","));
                    default -> throw new IllegalArgumentException("Unknown field: " + key);
                }
            }

            return new Resolution(disputeId, outcome, resolvedAt, observationsCount,
                    Collections.unmodifiableList(fieldResults),
                    majorityNodeIds, minorityNodeIds);
        }
    }

    /**
     * Resolve a dispute using majority consensus.
     *
     * @param dispute      the dispute to resolve
     * @param observations at least 3 independent observations of the same URL
     * @param resolvedAt   timestamp for the resolution
     * @return deterministic resolution result
     * @throws IllegalArgumentException if observations &lt; 3 or challenged record not found
     */
    public static Resolution resolve(DisputeRecord dispute, ObservationSet observations,
                                     Instant resolvedAt) {
        if (observations.size() < 3) {
            throw new IllegalArgumentException(
                    "At least 3 independent observations required, got " + observations.size());
        }

        // Find the challenged observation by record hash
        ObservationRecord challenged = null;
        for (ObservationRecord obs : observations.observations()) {
            if (obs.recordHash().equals(dispute.challengedRecordHash())) {
                challenged = obs;
                break;
            }
        }
        if (challenged == null) {
            throw new IllegalArgumentException(
                    "Challenged record not found in observation set");
        }

        // Compute per-field consensus
        List<FieldConsensus> fieldResults = new ArrayList<>();
        TreeSet<String> minorityNodes = new TreeSet<>();

        for (String field : CONSENSUS_FIELDS) {
            Map<String, List<String>> valueBuckets = new LinkedHashMap<>();
            for (ObservationRecord obs : observations.observations()) {
                String val = extractField(obs, field);
                valueBuckets.computeIfAbsent(val, k -> new ArrayList<>()).add(obs.nodeId());
            }

            String challengedValue = extractField(challenged, field);
            int total = observations.size();
            int threshold = total / 2;

            // Find majority value (at most one value can have > 50%)
            String majorityValue = null;
            int majorityCount = 0;
            for (Map.Entry<String, List<String>> entry : valueBuckets.entrySet()) {
                if (entry.getValue().size() > threshold) {
                    majorityValue = entry.getKey();
                    majorityCount = entry.getValue().size();
                    break;
                }
            }

            fieldResults.add(new FieldConsensus(
                    field, majorityValue, challengedValue, majorityCount, total));

            // Track minority nodes for this field
            if (majorityValue != null) {
                for (Map.Entry<String, List<String>> entry : valueBuckets.entrySet()) {
                    if (!entry.getKey().equals(majorityValue)) {
                        minorityNodes.addAll(entry.getValue());
                    }
                }
            }
        }

        // Determine outcome
        boolean anyInconclusive = fieldResults.stream().anyMatch(f -> !f.hasMajority());
        Outcome outcome;
        if (anyInconclusive) {
            outcome = Outcome.INCONCLUSIVE;
        } else {
            boolean challengedMatchesAll = fieldResults.stream()
                    .allMatch(FieldConsensus::challengedMatchesMajority);
            outcome = challengedMatchesAll ? Outcome.DISMISSED : Outcome.UPHELD;
        }

        // Build sorted majority/minority node lists
        List<String> majorityNodeIds = new ArrayList<>();
        for (ObservationRecord obs : observations.observations()) {
            if (!minorityNodes.contains(obs.nodeId())) {
                majorityNodeIds.add(obs.nodeId());
            }
        }
        Collections.sort(majorityNodeIds);
        List<String> minorityNodeIds = new ArrayList<>(minorityNodes);

        return new Resolution(
                dispute.disputeId(), outcome, resolvedAt,
                observations.size(),
                Collections.unmodifiableList(fieldResults),
                Collections.unmodifiableList(majorityNodeIds),
                Collections.unmodifiableList(minorityNodeIds)
        );
    }

    /**
     * Format a resolution as human-readable text.
     */
    public static String formatReport(Resolution resolution) {
        StringBuilder sb = new StringBuilder();
        sb.append(resolution.outcome().name()).append('\n');
        sb.append("dispute_id:").append(resolution.disputeId()).append('\n');
        sb.append("observations:").append(resolution.observationsCount()).append('\n');
        for (FieldConsensus fc : resolution.fieldResults()) {
            if (!fc.hasMajority()) {
                sb.append("  ").append(fc.field()).append(": NO_MAJORITY\n");
            } else if (!fc.challengedMatchesMajority()) {
                sb.append("  ").append(fc.field()).append(":\n");
                sb.append("    majority: ").append(fc.majorityValue())
                        .append(" (").append(fc.majorityCount()).append("/")
                        .append(fc.totalCount()).append(")\n");
                sb.append("    challenged: ").append(fc.challengedValue()).append('\n');
            }
        }
        sb.append("majority_nodes:").append(String.join(",", resolution.majorityNodeIds())).append('\n');
        sb.append("minority_nodes:").append(String.join(",", resolution.minorityNodeIds())).append('\n');
        return sb.toString();
    }

    static String extractField(ObservationRecord obs, String field) {
        return switch (field) {
            case "status_code" -> String.valueOf(obs.statusCode());
            case "content_hash" -> obs.contentHash();
            case "final_url" -> obs.finalUrl();
            case "directive:canonical" -> nullSafe(obs.directiveCanonical());
            case "directive:robots_meta" -> nullSafe(obs.directiveRobotsMeta());
            case "directive:robots_header" -> nullSafe(obs.directiveRobotsHeader());
            default -> throw new IllegalArgumentException("Unknown consensus field: " + field);
        };
    }

    private static String nullSafe(String s) {
        return s == null ? "" : s;
    }
}
