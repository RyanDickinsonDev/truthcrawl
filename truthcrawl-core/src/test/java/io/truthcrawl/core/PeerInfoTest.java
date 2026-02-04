package io.truthcrawl.core;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PeerInfoTest {

    private static final String NODE_ID = "a".repeat(64);
    private static final String ENDPOINT = "http://localhost:8080";
    private static final String PUB_KEY = "dGVzdGtleQ=="; // Base64 "testkey"

    @Test
    void basic_construction() {
        PeerInfo peer = new PeerInfo(NODE_ID, ENDPOINT, PUB_KEY);
        assertEquals(NODE_ID, peer.nodeId());
        assertEquals(ENDPOINT, peer.endpointUrl());
        assertEquals(PUB_KEY, peer.publicKeyBase64());
    }

    @Test
    void canonical_text_format() {
        PeerInfo peer = new PeerInfo(NODE_ID, ENDPOINT, PUB_KEY);
        String text = peer.toCanonicalText();
        assertTrue(text.contains("node_id:" + NODE_ID));
        assertTrue(text.contains("endpoint:" + ENDPOINT));
        assertTrue(text.contains("public_key:" + PUB_KEY));
    }

    @Test
    void parse_roundtrip() {
        PeerInfo original = new PeerInfo(NODE_ID, ENDPOINT, PUB_KEY);
        List<String> lines = List.of(original.toCanonicalText().split("\n"));
        PeerInfo parsed = PeerInfo.parse(lines);

        assertEquals(original.nodeId(), parsed.nodeId());
        assertEquals(original.endpointUrl(), parsed.endpointUrl());
        assertEquals(original.publicKeyBase64(), parsed.publicKeyBase64());
    }

    @Test
    void rejects_invalid_node_id() {
        assertThrows(IllegalArgumentException.class, () ->
                new PeerInfo("short", ENDPOINT, PUB_KEY));
    }

    @Test
    void rejects_null_node_id() {
        assertThrows(IllegalArgumentException.class, () ->
                new PeerInfo(null, ENDPOINT, PUB_KEY));
    }

    @Test
    void rejects_empty_endpoint() {
        assertThrows(IllegalArgumentException.class, () ->
                new PeerInfo(NODE_ID, "", PUB_KEY));
    }

    @Test
    void rejects_empty_public_key() {
        assertThrows(IllegalArgumentException.class, () ->
                new PeerInfo(NODE_ID, ENDPOINT, ""));
    }

    @Test
    void parse_rejects_wrong_line_count() {
        assertThrows(IllegalArgumentException.class, () ->
                PeerInfo.parse(List.of("node_id:" + NODE_ID)));
    }

    @Test
    void parse_rejects_wrong_field_order() {
        assertThrows(IllegalArgumentException.class, () ->
                PeerInfo.parse(List.of(
                        "endpoint:" + ENDPOINT,
                        "node_id:" + NODE_ID,
                        "public_key:" + PUB_KEY)));
    }

    @Test
    void endpoint_with_path() {
        PeerInfo peer = new PeerInfo(NODE_ID, "http://example.com:9090/api", PUB_KEY);
        assertEquals("http://example.com:9090/api", peer.endpointUrl());
    }
}
