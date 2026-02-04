package io.truthcrawl.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class IndexBuilderTest {

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
    void build_indexes_url_and_node() throws IOException {
        RecordStore store = new RecordStore(tempDir.resolve("store"));
        ObservationRecord r1 = makeRecord("https://a.com", "node1", "a".repeat(64));
        ObservationRecord r2 = makeRecord("https://b.com", "node2", "b".repeat(64));
        store.store(r1);
        store.store(r2);

        IndexBuilder.Index index = IndexBuilder.build(store);

        assertEquals(1, index.byUrl("https://a.com").size());
        assertEquals(1, index.byUrl("https://b.com").size());
        assertEquals(1, index.byNode("node1").size());
        assertEquals(1, index.byNode("node2").size());
    }

    @Test
    void multiple_records_same_url() throws IOException {
        RecordStore store = new RecordStore(tempDir.resolve("store"));
        store.store(makeRecord("https://a.com", "node1", "a".repeat(64)));
        store.store(makeRecord("https://a.com", "node2", "b".repeat(64)));

        IndexBuilder.Index index = IndexBuilder.build(store);
        assertEquals(2, index.byUrl("https://a.com").size());
    }

    @Test
    void multiple_records_same_node() throws IOException {
        RecordStore store = new RecordStore(tempDir.resolve("store"));
        store.store(makeRecord("https://a.com", "node1", "a".repeat(64)));
        store.store(makeRecord("https://b.com", "node1", "b".repeat(64)));

        IndexBuilder.Index index = IndexBuilder.build(store);
        assertEquals(2, index.byNode("node1").size());
    }

    @Test
    void byUrl_returns_empty_for_unknown() throws IOException {
        RecordStore store = new RecordStore(tempDir.resolve("store"));
        store.store(makeRecord("https://a.com", "node1", "a".repeat(64)));

        IndexBuilder.Index index = IndexBuilder.build(store);
        assertEquals(List.of(), index.byUrl("https://unknown.com"));
    }

    @Test
    void byNode_returns_empty_for_unknown() throws IOException {
        RecordStore store = new RecordStore(tempDir.resolve("store"));
        store.store(makeRecord("https://a.com", "node1", "a".repeat(64)));

        IndexBuilder.Index index = IndexBuilder.build(store);
        assertEquals(List.of(), index.byNode("unknown"));
    }

    @Test
    void urls_returns_sorted_unique() throws IOException {
        RecordStore store = new RecordStore(tempDir.resolve("store"));
        store.store(makeRecord("https://c.com", "node1", "c".repeat(64)));
        store.store(makeRecord("https://a.com", "node1", "a".repeat(64)));
        store.store(makeRecord("https://b.com", "node1", "b".repeat(64)));

        IndexBuilder.Index index = IndexBuilder.build(store);
        List<String> urls = index.urls();
        assertEquals(3, urls.size());
        assertEquals("https://a.com", urls.get(0));
        assertEquals("https://b.com", urls.get(1));
        assertEquals("https://c.com", urls.get(2));
    }

    @Test
    void nodeIds_returns_sorted_unique() throws IOException {
        RecordStore store = new RecordStore(tempDir.resolve("store"));
        store.store(makeRecord("https://a.com", "node_c", "a".repeat(64)));
        store.store(makeRecord("https://b.com", "node_a", "b".repeat(64)));
        store.store(makeRecord("https://c.com", "node_b", "c".repeat(64)));

        IndexBuilder.Index index = IndexBuilder.build(store);
        List<String> nodes = index.nodeIds();
        assertEquals(3, nodes.size());
        assertEquals("node_a", nodes.get(0));
        assertEquals("node_b", nodes.get(1));
        assertEquals("node_c", nodes.get(2));
    }

    @Test
    void hashes_within_url_are_sorted() throws IOException {
        RecordStore store = new RecordStore(tempDir.resolve("store"));
        store.store(makeRecord("https://a.com", "node1", "a".repeat(64)));
        store.store(makeRecord("https://a.com", "node2", "b".repeat(64)));
        store.store(makeRecord("https://a.com", "node3", "c".repeat(64)));

        IndexBuilder.Index index = IndexBuilder.build(store);
        List<String> hashes = index.byUrl("https://a.com");
        for (int i = 1; i < hashes.size(); i++) {
            assertTrue(hashes.get(i).compareTo(hashes.get(i - 1)) >= 0);
        }
    }

    @Test
    void empty_store_produces_empty_index() throws IOException {
        RecordStore store = new RecordStore(tempDir.resolve("store"));
        IndexBuilder.Index index = IndexBuilder.build(store);
        assertEquals(List.of(), index.urls());
        assertEquals(List.of(), index.nodeIds());
    }

    @Test
    void index_is_deterministic() throws IOException {
        RecordStore store = new RecordStore(tempDir.resolve("store"));
        store.store(makeRecord("https://a.com", "node1", "a".repeat(64)));
        store.store(makeRecord("https://b.com", "node2", "b".repeat(64)));

        IndexBuilder.Index i1 = IndexBuilder.build(store);
        IndexBuilder.Index i2 = IndexBuilder.build(store);

        assertEquals(i1.byUrl("https://a.com"), i2.byUrl("https://a.com"));
        assertEquals(i1.byUrl("https://b.com"), i2.byUrl("https://b.com"));
        assertEquals(i1.byNode("node1"), i2.byNode("node1"));
        assertEquals(i1.byNode("node2"), i2.byNode("node2"));
    }
}
