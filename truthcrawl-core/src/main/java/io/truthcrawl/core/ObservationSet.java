package io.truthcrawl.core;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * A collection of independent observations of the same URL from different nodes.
 *
 * <p>Used as evidence in dispute resolution. Validates that:
 * <ul>
 *   <li>All observations are for the same URL.</li>
 *   <li>All observations are from distinct nodes.</li>
 *   <li>The set is non-empty.</li>
 * </ul>
 */
public final class ObservationSet {

    private final String url;
    private final List<ObservationRecord> observations;

    private ObservationSet(String url, List<ObservationRecord> observations) {
        this.url = url;
        this.observations = observations;
    }

    /**
     * Create an observation set from a list of records.
     *
     * @param records non-empty list of signed observations for the same URL from distinct nodes
     * @return a validated ObservationSet
     * @throws IllegalArgumentException if validation fails
     */
    public static ObservationSet of(List<ObservationRecord> records) {
        if (records.isEmpty()) {
            throw new IllegalArgumentException("Observation set must not be empty");
        }

        String url = records.get(0).url();
        Set<String> nodeIds = new HashSet<>();

        for (ObservationRecord r : records) {
            if (!r.url().equals(url)) {
                throw new IllegalArgumentException(
                        "All observations must be for the same URL. Expected " + url + ", got " + r.url());
            }
            if (!nodeIds.add(r.nodeId())) {
                throw new IllegalArgumentException(
                        "Duplicate node_id in observation set: " + r.nodeId());
            }
        }

        return new ObservationSet(url, Collections.unmodifiableList(List.copyOf(records)));
    }

    /**
     * The URL all observations are for.
     */
    public String url() {
        return url;
    }

    /**
     * The observations, in insertion order.
     */
    public List<ObservationRecord> observations() {
        return observations;
    }

    /**
     * Number of independent observations.
     */
    public int size() {
        return observations.size();
    }
}
