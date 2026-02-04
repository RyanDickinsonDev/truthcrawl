package io.truthcrawl.core;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class RequestVerifierTest {

    private PublisherKey peerKey;
    private RequestSigner signer;
    private PeerRegistry registry;
    private RequestVerifier verifier;

    @BeforeEach
    void setup(@TempDir Path tmp) throws IOException {
        peerKey = PublisherKey.generate();
        signer = new RequestSigner(peerKey);

        registry = new PeerRegistry(tmp.resolve("peers"));
        PeerInfo peer = new PeerInfo(signer.nodeId(), "http://localhost:8080", peerKey.publicKeyBase64());
        registry.register(peer);

        verifier = new RequestVerifier(registry);
    }

    @Test
    void valid_signed_request() {
        Instant now = Instant.now();
        byte[] body = "test body".getBytes(StandardCharsets.UTF_8);
        RequestSigner.SignedHeaders headers = signer.sign("POST", "/peers", now, body);

        RequestVerifier.Result result = verifier.verify(
                "POST", "/peers", headers.nodeId(), headers.timestamp(), headers.signature(), body, now);

        assertTrue(result.valid());
        assertEquals(signer.nodeId(), result.nodeId());
        assertTrue(result.errors().isEmpty());
    }

    @Test
    void rejects_missing_node_id() {
        Instant now = Instant.now();
        RequestVerifier.Result result = verifier.verify(
                "POST", "/peers", null, now.toString(), "sig", new byte[0], now);

        assertFalse(result.valid());
        assertTrue(result.errors().stream().anyMatch(e -> e.contains("X-Node-Id")));
    }

    @Test
    void rejects_missing_timestamp() {
        Instant now = Instant.now();
        RequestVerifier.Result result = verifier.verify(
                "POST", "/peers", signer.nodeId(), null, "sig", new byte[0], now);

        assertFalse(result.valid());
        assertTrue(result.errors().stream().anyMatch(e -> e.contains("X-Timestamp")));
    }

    @Test
    void rejects_missing_signature() {
        Instant now = Instant.now();
        RequestVerifier.Result result = verifier.verify(
                "POST", "/peers", signer.nodeId(), now.toString(), null, new byte[0], now);

        assertFalse(result.valid());
        assertTrue(result.errors().stream().anyMatch(e -> e.contains("X-Signature")));
    }

    @Test
    void rejects_stale_timestamp() {
        Instant oldTime = Instant.now().minusSeconds(600); // 10 minutes ago
        byte[] body = new byte[0];
        RequestSigner.SignedHeaders headers = signer.sign("POST", "/peers", oldTime, body);

        RequestVerifier.Result result = verifier.verify(
                "POST", "/peers", headers.nodeId(), headers.timestamp(), headers.signature(), body, Instant.now());

        assertFalse(result.valid());
        assertTrue(result.errors().stream().anyMatch(e -> e.contains("too far")));
    }

    @Test
    void rejects_unknown_peer() {
        PublisherKey unknownKey = PublisherKey.generate();
        RequestSigner unknownSigner = new RequestSigner(unknownKey);
        Instant now = Instant.now();
        byte[] body = new byte[0];
        RequestSigner.SignedHeaders headers = unknownSigner.sign("POST", "/peers", now, body);

        RequestVerifier.Result result = verifier.verify(
                "POST", "/peers", headers.nodeId(), headers.timestamp(), headers.signature(), body, now);

        assertFalse(result.valid());
        assertTrue(result.errors().stream().anyMatch(e -> e.contains("Unknown peer")));
    }

    @Test
    void rejects_invalid_signature() {
        Instant now = Instant.now();
        byte[] body = "correct body".getBytes(StandardCharsets.UTF_8);
        RequestSigner.SignedHeaders headers = signer.sign("POST", "/peers", now, body);

        // Verify with different body (signature won't match)
        byte[] wrongBody = "wrong body".getBytes(StandardCharsets.UTF_8);
        RequestVerifier.Result result = verifier.verify(
                "POST", "/peers", headers.nodeId(), headers.timestamp(), headers.signature(), wrongBody, now);

        assertFalse(result.valid());
        assertTrue(result.errors().stream().anyMatch(e -> e.contains("Invalid request signature")));
    }

    @Test
    void rejects_wrong_method() {
        Instant now = Instant.now();
        byte[] body = "body".getBytes(StandardCharsets.UTF_8);
        RequestSigner.SignedHeaders headers = signer.sign("POST", "/peers", now, body);

        // Verify with different method
        RequestVerifier.Result result = verifier.verify(
                "GET", "/peers", headers.nodeId(), headers.timestamp(), headers.signature(), body, now);

        assertFalse(result.valid());
    }

    @Test
    void rejects_wrong_path() {
        Instant now = Instant.now();
        byte[] body = "body".getBytes(StandardCharsets.UTF_8);
        RequestSigner.SignedHeaders headers = signer.sign("POST", "/peers", now, body);

        RequestVerifier.Result result = verifier.verify(
                "POST", "/other", headers.nodeId(), headers.timestamp(), headers.signature(), body, now);

        assertFalse(result.valid());
    }

    @Test
    void accepts_within_clock_skew() {
        Instant now = Instant.now();
        Instant slightlyOff = now.minusSeconds(120); // 2 minutes ago, within 5 min window
        byte[] body = new byte[0];
        RequestSigner.SignedHeaders headers = signer.sign("POST", "/peers", slightlyOff, body);

        RequestVerifier.Result result = verifier.verify(
                "POST", "/peers", headers.nodeId(), headers.timestamp(), headers.signature(), body, now);

        assertTrue(result.valid());
    }
}
