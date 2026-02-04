package io.truthcrawl.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PeerRegistryTest {

    private static final String NODE_A = "a".repeat(64);
    private static final String NODE_B = "b".repeat(64);
    private static final String ENDPOINT = "http://localhost:8080";
    private static final String PUB_KEY = "dGVzdGtleQ==";

    @Test
    void register_and_load(@TempDir Path tmp) throws IOException {
        PeerRegistry registry = new PeerRegistry(tmp.resolve("peers"));
        PeerInfo peer = new PeerInfo(NODE_A, ENDPOINT, PUB_KEY);

        registry.register(peer);

        PeerInfo loaded = registry.load(NODE_A);
        assertNotNull(loaded);
        assertEquals(NODE_A, loaded.nodeId());
        assertEquals(ENDPOINT, loaded.endpointUrl());
    }

    @Test
    void load_nonexistent_returns_null(@TempDir Path tmp) throws IOException {
        PeerRegistry registry = new PeerRegistry(tmp.resolve("peers"));
        assertNull(registry.load(NODE_A));
    }

    @Test
    void contains(@TempDir Path tmp) throws IOException {
        PeerRegistry registry = new PeerRegistry(tmp.resolve("peers"));
        PeerInfo peer = new PeerInfo(NODE_A, ENDPOINT, PUB_KEY);
        registry.register(peer);

        assertTrue(registry.contains(NODE_A));
        assertFalse(registry.contains(NODE_B));
    }

    @Test
    void reregister_updates_endpoint(@TempDir Path tmp) throws IOException {
        PeerRegistry registry = new PeerRegistry(tmp.resolve("peers"));
        registry.register(new PeerInfo(NODE_A, "http://old:8080", PUB_KEY));
        registry.register(new PeerInfo(NODE_A, "http://new:9090", PUB_KEY));

        PeerInfo loaded = registry.load(NODE_A);
        assertEquals("http://new:9090", loaded.endpointUrl());
    }

    @Test
    void list_sorted(@TempDir Path tmp) throws IOException {
        PeerRegistry registry = new PeerRegistry(tmp.resolve("peers"));
        registry.register(new PeerInfo(NODE_B, ENDPOINT, PUB_KEY));
        registry.register(new PeerInfo(NODE_A, ENDPOINT, PUB_KEY));

        List<String> ids = registry.listNodeIds();
        assertEquals(2, ids.size());
        assertEquals(NODE_A, ids.get(0));
        assertEquals(NODE_B, ids.get(1));
    }

    @Test
    void empty_registry(@TempDir Path tmp) throws IOException {
        PeerRegistry registry = new PeerRegistry(tmp.resolve("peers"));
        assertTrue(registry.listNodeIds().isEmpty());
        assertEquals(0, registry.size());
    }

    @Test
    void size(@TempDir Path tmp) throws IOException {
        PeerRegistry registry = new PeerRegistry(tmp.resolve("peers"));
        registry.register(new PeerInfo(NODE_A, ENDPOINT, PUB_KEY));
        registry.register(new PeerInfo(NODE_B, ENDPOINT, PUB_KEY));
        assertEquals(2, registry.size());
    }
}
