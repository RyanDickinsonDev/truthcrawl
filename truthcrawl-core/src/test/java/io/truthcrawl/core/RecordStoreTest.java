package io.truthcrawl.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RecordStoreTest {

    @TempDir
    Path tempDir;

    private ObservationRecord makeRecord(String url, String nodeId) {
        return ObservationRecord.builder()
                .version("0.1")
                .observedAt(Instant.parse("2024-01-15T12:00:00Z"))
                .url(url)
                .finalUrl(url)
                .statusCode(200)
                .fetchMs(100)
                .contentHash("a".repeat(64))
                .nodeId(nodeId)
                .build();
    }

    @Test
    void store_and_load_roundtrip() throws IOException {
        RecordStore store = new RecordStore(tempDir.resolve("store"));
        ObservationRecord record = makeRecord("https://example.com", "node1");

        String hash = store.store(record);
        assertNotNull(hash);
        assertEquals(64, hash.length());

        ObservationRecord loaded = store.load(hash);
        assertNotNull(loaded);
        assertEquals(record.url(), loaded.url());
        assertEquals(record.nodeId(), loaded.nodeId());
        assertEquals(record.recordHash(), loaded.recordHash());
    }

    @Test
    void store_is_idempotent() throws IOException {
        RecordStore store = new RecordStore(tempDir.resolve("store"));
        ObservationRecord record = makeRecord("https://example.com", "node1");

        String hash1 = store.store(record);
        String hash2 = store.store(record);
        assertEquals(hash1, hash2);
        assertEquals(1, store.size());
    }

    @Test
    void load_returns_null_for_missing() throws IOException {
        RecordStore store = new RecordStore(tempDir.resolve("store"));
        assertNull(store.load("ff" + "0".repeat(62)));
    }

    @Test
    void contains_true_after_store() throws IOException {
        RecordStore store = new RecordStore(tempDir.resolve("store"));
        ObservationRecord record = makeRecord("https://example.com", "node1");
        String hash = store.store(record);

        assertTrue(store.contains(hash));
    }

    @Test
    void contains_false_when_absent() {
        RecordStore store = new RecordStore(tempDir.resolve("store"));
        assertFalse(store.contains("ff" + "0".repeat(62)));
    }

    @Test
    void listHashes_sorted() throws IOException {
        RecordStore store = new RecordStore(tempDir.resolve("store"));
        ObservationRecord r1 = makeRecord("https://aaa.com", "node1");
        ObservationRecord r2 = makeRecord("https://bbb.com", "node2");
        ObservationRecord r3 = makeRecord("https://ccc.com", "node3");

        store.store(r1);
        store.store(r2);
        store.store(r3);

        List<String> hashes = store.listHashes();
        assertEquals(3, hashes.size());
        // verify sorted
        for (int i = 1; i < hashes.size(); i++) {
            assertTrue(hashes.get(i).compareTo(hashes.get(i - 1)) >= 0);
        }
    }

    @Test
    void listHashes_empty_when_no_store_dir() throws IOException {
        RecordStore store = new RecordStore(tempDir.resolve("nonexistent"));
        assertEquals(List.of(), store.listHashes());
    }

    @Test
    void size_matches_stored_count() throws IOException {
        RecordStore store = new RecordStore(tempDir.resolve("store"));
        assertEquals(0, store.size());

        store.store(makeRecord("https://a.com", "node1"));
        assertEquals(1, store.size());

        store.store(makeRecord("https://b.com", "node2"));
        assertEquals(2, store.size());
    }

    @Test
    void file_layout_uses_hash_prefix_directory() throws IOException {
        RecordStore store = new RecordStore(tempDir.resolve("store"));
        ObservationRecord record = makeRecord("https://example.com", "node1");
        String hash = store.store(record);

        Path expected = tempDir.resolve("store")
                .resolve(hash.substring(0, 2))
                .resolve(hash + ".txt");
        assertTrue(Files.exists(expected));
    }

    @Test
    void different_records_different_hashes() throws IOException {
        RecordStore store = new RecordStore(tempDir.resolve("store"));
        String h1 = store.store(makeRecord("https://a.com", "node1"));
        String h2 = store.store(makeRecord("https://b.com", "node2"));
        assertNotEquals(h1, h2);
    }
}
