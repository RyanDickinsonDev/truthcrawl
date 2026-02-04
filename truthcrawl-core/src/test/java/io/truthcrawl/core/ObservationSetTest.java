package io.truthcrawl.core;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ObservationSetTest {

    private static final Instant T1 = Instant.parse("2024-01-15T12:00:00Z");
    private static final String CONTENT_HASH =
            "b94d27b9934d3e08a52e52d7da7dabfac484efe37a5380ee9088f7ace2efcde9";

    private ObservationRecord record(String nodeId) {
        return ObservationRecord.builder()
                .version("0.1")
                .observedAt(T1)
                .url("https://example.com")
                .finalUrl("https://example.com/")
                .statusCode(200)
                .fetchMs(100)
                .contentHash(CONTENT_HASH)
                .nodeId(nodeId)
                .build();
    }

    @Test
    void creates_set_from_valid_records() {
        ObservationSet set = ObservationSet.of(List.of(
                record("aaaa" + "0".repeat(60)),
                record("bbbb" + "0".repeat(60)),
                record("cccc" + "0".repeat(60))
        ));
        assertEquals(3, set.size());
        assertEquals("https://example.com", set.url());
    }

    @Test
    void rejects_empty_list() {
        assertThrows(IllegalArgumentException.class, () ->
                ObservationSet.of(List.of()));
    }

    @Test
    void rejects_different_urls() {
        ObservationRecord r1 = record("aaaa" + "0".repeat(60));
        ObservationRecord r2 = ObservationRecord.builder()
                .version("0.1")
                .observedAt(T1)
                .url("https://other.com")
                .finalUrl("https://other.com/")
                .statusCode(200)
                .fetchMs(100)
                .contentHash(CONTENT_HASH)
                .nodeId("bbbb" + "0".repeat(60))
                .build();

        assertThrows(IllegalArgumentException.class, () ->
                ObservationSet.of(List.of(r1, r2)));
    }

    @Test
    void rejects_duplicate_node_ids() {
        String nodeId = "aaaa" + "0".repeat(60);
        assertThrows(IllegalArgumentException.class, () ->
                ObservationSet.of(List.of(record(nodeId), record(nodeId))));
    }

    @Test
    void observations_are_unmodifiable() {
        ObservationSet set = ObservationSet.of(List.of(
                record("aaaa" + "0".repeat(60)),
                record("bbbb" + "0".repeat(60))
        ));
        assertThrows(UnsupportedOperationException.class, () ->
                set.observations().clear());
    }
}
