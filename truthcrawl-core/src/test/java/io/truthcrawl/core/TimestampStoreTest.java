package io.truthcrawl.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TimestampStoreTest {

    @TempDir
    Path tempDir;

    private TimestampToken makeToken(String dataHash) {
        PublisherKey key = PublisherKey.generate();
        TimestampAuthority tsa = new TimestampAuthority(key);
        return tsa.issue(dataHash, Instant.parse("2024-01-15T12:00:00Z"));
    }

    @Test
    void store_and_load_roundtrip() throws IOException {
        TimestampStore store = new TimestampStore(tempDir.resolve("ts"));
        TimestampToken token = makeToken("a".repeat(64));

        store.store(token);
        TimestampToken loaded = store.load("a".repeat(64));

        assertNotNull(loaded);
        assertEquals(token.dataHash(), loaded.dataHash());
        assertEquals(token.issuedAt(), loaded.issuedAt());
        assertEquals(token.tsaSignature(), loaded.tsaSignature());
    }

    @Test
    void first_write_wins() throws IOException {
        TimestampStore store = new TimestampStore(tempDir.resolve("ts"));

        PublisherKey key1 = PublisherKey.generate();
        TimestampAuthority tsa1 = new TimestampAuthority(key1);
        TimestampToken first = tsa1.issue("a".repeat(64), Instant.parse("2024-01-15T12:00:00Z"));

        PublisherKey key2 = PublisherKey.generate();
        TimestampAuthority tsa2 = new TimestampAuthority(key2);
        TimestampToken second = tsa2.issue("a".repeat(64), Instant.parse("2024-01-16T12:00:00Z"));

        store.store(first);
        store.store(second); // should be no-op

        TimestampToken loaded = store.load("a".repeat(64));
        assertEquals(first.tsaKeyId(), loaded.tsaKeyId());
    }

    @Test
    void load_returns_null_when_absent() throws IOException {
        TimestampStore store = new TimestampStore(tempDir.resolve("ts"));
        assertNull(store.load("f".repeat(64)));
    }

    @Test
    void contains_true_after_store() throws IOException {
        TimestampStore store = new TimestampStore(tempDir.resolve("ts"));
        TimestampToken token = makeToken("a".repeat(64));
        store.store(token);
        assertTrue(store.contains("a".repeat(64)));
    }

    @Test
    void contains_false_when_absent() {
        TimestampStore store = new TimestampStore(tempDir.resolve("ts"));
        assertFalse(store.contains("f".repeat(64)));
    }

    @Test
    void listHashes_sorted() throws IOException {
        TimestampStore store = new TimestampStore(tempDir.resolve("ts"));
        store.store(makeToken("c".repeat(64)));
        store.store(makeToken("a".repeat(64)));
        store.store(makeToken("b".repeat(64)));

        List<String> hashes = store.listHashes();
        assertEquals(3, hashes.size());
        assertEquals("a".repeat(64), hashes.get(0));
        assertEquals("b".repeat(64), hashes.get(1));
        assertEquals("c".repeat(64), hashes.get(2));
    }

    @Test
    void listHashes_empty_when_no_dir() throws IOException {
        TimestampStore store = new TimestampStore(tempDir.resolve("nonexistent"));
        assertEquals(List.of(), store.listHashes());
    }

    @Test
    void size_matches_count() throws IOException {
        TimestampStore store = new TimestampStore(tempDir.resolve("ts"));
        assertEquals(0, store.size());
        store.store(makeToken("a".repeat(64)));
        assertEquals(1, store.size());
        store.store(makeToken("b".repeat(64)));
        assertEquals(2, store.size());
    }
}
