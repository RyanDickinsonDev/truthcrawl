package io.truthcrawl.core;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Builds in-memory indices from a {@link RecordStore}.
 *
 * <p>Indices:
 * <ul>
 *   <li>URL index: maps URL → sorted list of record hashes</li>
 *   <li>Node index: maps node_id → sorted list of record hashes</li>
 * </ul>
 *
 * <p>Indices are deterministic: same store contents always produce the same indices.
 */
public final class IndexBuilder {

    private IndexBuilder() {}

    /**
     * Index query result containing both URL and node indices.
     *
     * @param urlIndex  URL → sorted list of record hashes
     * @param nodeIndex node_id → sorted list of record hashes
     */
    public record Index(
            Map<String, List<String>> urlIndex,
            Map<String, List<String>> nodeIndex
    ) {
        /**
         * Get record hashes for a URL.
         *
         * @return sorted list of record hashes, or empty list if URL not found
         */
        public List<String> byUrl(String url) {
            return urlIndex.getOrDefault(url, List.of());
        }

        /**
         * Get record hashes for a node.
         *
         * @return sorted list of record hashes, or empty list if node not found
         */
        public List<String> byNode(String nodeId) {
            return nodeIndex.getOrDefault(nodeId, List.of());
        }

        /**
         * All unique URLs in the index, sorted.
         */
        public List<String> urls() {
            return List.copyOf(urlIndex.keySet());
        }

        /**
         * All unique node IDs in the index, sorted.
         */
        public List<String> nodeIds() {
            return List.copyOf(nodeIndex.keySet());
        }
    }

    /**
     * Build indices by scanning all records in the store.
     *
     * @param store the record store to scan
     * @return the built indices
     * @throws IOException if reading fails
     */
    public static Index build(RecordStore store) throws IOException {
        Map<String, List<String>> urlMap = new TreeMap<>();
        Map<String, List<String>> nodeMap = new TreeMap<>();

        for (String hash : store.listHashes()) {
            ObservationRecord record = store.load(hash);
            if (record == null) continue;

            urlMap.computeIfAbsent(record.url(), k -> new ArrayList<>()).add(hash);
            nodeMap.computeIfAbsent(record.nodeId(), k -> new ArrayList<>()).add(hash);
        }

        // Sort all lists and make immutable
        Map<String, List<String>> urlIndex = new LinkedHashMap<>();
        for (Map.Entry<String, List<String>> e : urlMap.entrySet()) {
            Collections.sort(e.getValue());
            urlIndex.put(e.getKey(), Collections.unmodifiableList(e.getValue()));
        }

        Map<String, List<String>> nodeIndex = new LinkedHashMap<>();
        for (Map.Entry<String, List<String>> e : nodeMap.entrySet()) {
            Collections.sort(e.getValue());
            nodeIndex.put(e.getKey(), Collections.unmodifiableList(e.getValue()));
        }

        return new Index(
                Collections.unmodifiableMap(urlIndex),
                Collections.unmodifiableMap(nodeIndex)
        );
    }
}
