package io.truthcrawl.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class NodeProfileStoreTest {

    private static final Instant TIME = Instant.parse("2024-06-01T10:00:00Z");

    @TempDir
    Path tempDir;

    @Test
    void store_and_load() throws Exception {
        NodeProfileStore store = new NodeProfileStore(tempDir.resolve("profiles"));
        PublisherKey key = PublisherKey.generate();
        NodeRegistration reg = NodeRegistration.create("Alice", "ACME", "a@b.com", key, TIME);
        NodeProfile profile = new NodeProfile(reg, null);

        store.store(profile);
        NodeProfile loaded = store.load(profile.nodeId());

        assertNotNull(loaded);
        assertEquals(profile.nodeId(), loaded.nodeId());
        assertEquals("Alice", loaded.registration().operatorName());
    }

    @Test
    void load_nonexistent_returns_null() throws Exception {
        NodeProfileStore store = new NodeProfileStore(tempDir.resolve("profiles"));
        assertNull(store.load("a".repeat(64)));
    }

    @Test
    void contains() throws Exception {
        NodeProfileStore store = new NodeProfileStore(tempDir.resolve("profiles"));
        PublisherKey key = PublisherKey.generate();
        NodeRegistration reg = NodeRegistration.create("Alice", "ACME", "a@b.com", key, TIME);
        NodeProfile profile = new NodeProfile(reg, null);

        assertFalse(store.contains(profile.nodeId()));
        store.store(profile);
        assertTrue(store.contains(profile.nodeId()));
    }

    @Test
    void list_node_ids_sorted() throws Exception {
        NodeProfileStore store = new NodeProfileStore(tempDir.resolve("profiles"));

        PublisherKey key1 = PublisherKey.generate();
        PublisherKey key2 = PublisherKey.generate();
        NodeProfile p1 = new NodeProfile(
                NodeRegistration.create("A", "Org1", "a@b.com", key1, TIME), null);
        NodeProfile p2 = new NodeProfile(
                NodeRegistration.create("B", "Org2", "b@c.com", key2, TIME), null);

        store.store(p1);
        store.store(p2);

        List<String> ids = store.listNodeIds();
        assertEquals(2, ids.size());
        // Verify sorted
        assertTrue(ids.get(0).compareTo(ids.get(1)) <= 0);
    }

    @Test
    void size() throws Exception {
        NodeProfileStore store = new NodeProfileStore(tempDir.resolve("profiles"));
        assertEquals(0, store.size());

        PublisherKey key = PublisherKey.generate();
        NodeProfile profile = new NodeProfile(
                NodeRegistration.create("Alice", "ACME", "a@b.com", key, TIME), null);
        store.store(profile);
        assertEquals(1, store.size());
    }

    @Test
    void store_with_attestation() throws Exception {
        NodeProfileStore store = new NodeProfileStore(tempDir.resolve("profiles"));
        PublisherKey key = PublisherKey.generate();
        NodeRegistration reg = NodeRegistration.create("Alice", "ACME", "a@b.com", key, TIME);
        CrawlAttestation att = CrawlAttestation.create(key, List.of("example.com"), TIME);
        NodeProfile profile = new NodeProfile(reg, att);

        store.store(profile);
        NodeProfile loaded = store.load(profile.nodeId());

        assertNotNull(loaded.attestation());
        assertEquals(List.of("example.com"), loaded.attestation().domains());
    }

    @Test
    void overwrite_profile() throws Exception {
        NodeProfileStore store = new NodeProfileStore(tempDir.resolve("profiles"));
        PublisherKey key = PublisherKey.generate();
        NodeRegistration reg = NodeRegistration.create("Alice", "ACME", "a@b.com", key, TIME);
        NodeProfile profileV1 = new NodeProfile(reg, null);
        store.store(profileV1);

        CrawlAttestation att = CrawlAttestation.create(key, List.of("example.com"), TIME);
        NodeProfile profileV2 = new NodeProfile(reg, att);
        store.store(profileV2);

        NodeProfile loaded = store.load(reg.nodeId());
        assertNotNull(loaded.attestation());
    }

    @Test
    void empty_directory_returns_empty_list() throws Exception {
        NodeProfileStore store = new NodeProfileStore(tempDir.resolve("nonexistent"));
        assertEquals(List.of(), store.listNodeIds());
    }
}
