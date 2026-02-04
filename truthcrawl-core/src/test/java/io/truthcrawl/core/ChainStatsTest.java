package io.truthcrawl.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ChainStatsTest {

    @TempDir
    Path tempDir;

    private ObservationRecord makeRecord(String url, String nodeId, String contentHash) {
        return ObservationRecord.builder()
                .version("0.1")
                .observedAt(Instant.parse("2024-01-15T12:00:00Z"))
                .url(url)
                .finalUrl(url)
                .statusCode(200)
                .fetchMs(100)
                .contentHash(contentHash)
                .nodeId(nodeId)
                .build();
    }

    @Test
    void canonical_text_format() {
        ChainStats stats = new ChainStats(5, 150, 42, 8);
        String text = stats.toCanonicalText();
        assertEquals("total_batches:5\ntotal_records:150\nunique_urls:42\nunique_nodes:8\n", text);
    }

    @Test
    void hash_determinism() {
        ChainStats s1 = new ChainStats(5, 150, 42, 8);
        ChainStats s2 = new ChainStats(5, 150, 42, 8);
        assertEquals(s1.statsHash(), s2.statsHash());
    }

    @Test
    void hash_is_64_char_hex() {
        ChainStats stats = new ChainStats(5, 150, 42, 8);
        String hash = stats.statsHash();
        assertEquals(64, hash.length());
        assertTrue(hash.matches("[0-9a-f]{64}"));
    }

    @Test
    void different_stats_different_hash() {
        ChainStats s1 = new ChainStats(5, 150, 42, 8);
        ChainStats s2 = new ChainStats(6, 150, 42, 8);
        assertNotEquals(s1.statsHash(), s2.statsHash());
    }

    @Test
    void parse_roundtrip() {
        ChainStats original = new ChainStats(5, 150, 42, 8);
        List<String> lines = List.of(original.toCanonicalText().split("\n"));
        ChainStats parsed = ChainStats.parse(lines);

        assertEquals(original.totalBatches(), parsed.totalBatches());
        assertEquals(original.totalRecords(), parsed.totalRecords());
        assertEquals(original.uniqueUrls(), parsed.uniqueUrls());
        assertEquals(original.uniqueNodes(), parsed.uniqueNodes());
    }

    @Test
    void parse_ignores_blank_lines() {
        List<String> lines = List.of(
                "total_batches:5", "", "total_records:150",
                "  ", "unique_urls:42", "unique_nodes:8"
        );
        ChainStats stats = ChainStats.parse(lines);
        assertEquals(5, stats.totalBatches());
    }

    @Test
    void parse_rejects_wrong_line_count() {
        List<String> lines = List.of("total_batches:5", "total_records:150");
        assertThrows(IllegalArgumentException.class, () -> ChainStats.parse(lines));
    }

    @Test
    void format_report() {
        ChainStats stats = new ChainStats(5, 150, 42, 8);
        String report = stats.formatReport();
        assertTrue(report.contains("Batches:      5"));
        assertTrue(report.contains("Records:      150"));
        assertTrue(report.contains("Unique URLs:  42"));
        assertTrue(report.contains("Unique nodes: 8"));
    }

    @Test
    void compute_from_index() throws IOException {
        RecordStore store = new RecordStore(tempDir.resolve("store"));
        store.store(makeRecord("https://a.com", "node1", "a".repeat(64)));
        store.store(makeRecord("https://a.com", "node2", "b".repeat(64)));
        store.store(makeRecord("https://b.com", "node1", "c".repeat(64)));

        IndexBuilder.Index index = IndexBuilder.build(store);
        ChainStats stats = ChainStats.compute(3, index);

        assertEquals(3, stats.totalBatches());
        assertEquals(3, stats.totalRecords());
        assertEquals(2, stats.uniqueUrls());
        assertEquals(2, stats.uniqueNodes());
    }

    @Test
    void compute_from_store() throws IOException {
        RecordStore store = new RecordStore(tempDir.resolve("store"));
        store.store(makeRecord("https://a.com", "node1", "a".repeat(64)));
        store.store(makeRecord("https://b.com", "node2", "b".repeat(64)));

        ChainStats stats = ChainStats.compute(1, store);

        assertEquals(1, stats.totalBatches());
        assertEquals(2, stats.totalRecords());
        assertEquals(2, stats.uniqueUrls());
        assertEquals(2, stats.uniqueNodes());
    }
}
