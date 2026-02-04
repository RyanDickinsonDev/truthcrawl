package io.truthcrawl.core;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class RequestSignerTest {

    private static PublisherKey key;
    private static RequestSigner signer;

    @BeforeAll
    static void setup() {
        key = PublisherKey.generate();
        signer = new RequestSigner(key);
    }

    @Test
    void node_id_is_64_hex() {
        assertEquals(64, signer.nodeId().length());
        assertTrue(signer.nodeId().matches("[0-9a-f]{64}"));
    }

    @Test
    void node_id_is_deterministic() {
        RequestSigner signer2 = new RequestSigner(key);
        assertEquals(signer.nodeId(), signer2.nodeId());
    }

    @Test
    void sign_produces_valid_headers() {
        Instant now = Instant.now();
        byte[] body = "test body".getBytes(StandardCharsets.UTF_8);

        RequestSigner.SignedHeaders headers = signer.sign("POST", "/peers", now, body);

        assertEquals(signer.nodeId(), headers.nodeId());
        assertNotNull(headers.timestamp());
        assertNotNull(headers.signature());
        assertFalse(headers.signature().isEmpty());
    }

    @Test
    void signature_verifies_with_same_key() {
        Instant now = Instant.now();
        byte[] body = "request body".getBytes(StandardCharsets.UTF_8);

        RequestSigner.SignedHeaders headers = signer.sign("POST", "/peers", now, body);

        String bodyHash = RequestSigner.hashBytes(body);
        byte[] signingInput = RequestSigner.buildSigningInput("POST", "/peers", headers.timestamp(), bodyHash);
        assertTrue(key.verify(signingInput, headers.signature()));
    }

    @Test
    void signature_fails_with_different_key() {
        Instant now = Instant.now();
        byte[] body = "request body".getBytes(StandardCharsets.UTF_8);

        RequestSigner.SignedHeaders headers = signer.sign("POST", "/peers", now, body);

        PublisherKey otherKey = PublisherKey.generate();
        String bodyHash = RequestSigner.hashBytes(body);
        byte[] signingInput = RequestSigner.buildSigningInput("POST", "/peers", headers.timestamp(), bodyHash);
        assertFalse(otherKey.verify(signingInput, headers.signature()));
    }

    @Test
    void signing_input_is_versioned() {
        byte[] input = RequestSigner.buildSigningInput("GET", "/info", "2024-01-01T00:00:00Z", "a".repeat(64));
        String text = new String(input, StandardCharsets.UTF_8);
        assertTrue(text.startsWith("truthcrawl-auth-v1\n"));
    }

    @Test
    void signing_input_contains_all_fields() {
        byte[] input = RequestSigner.buildSigningInput("POST", "/peers", "2024-01-01T00:00:00Z", "b".repeat(64));
        String text = new String(input, StandardCharsets.UTF_8);
        assertTrue(text.contains("POST\n"));
        assertTrue(text.contains("/peers\n"));
        assertTrue(text.contains("2024-01-01T00:00:00Z\n"));
        assertTrue(text.contains("b".repeat(64) + "\n"));
    }

    @Test
    void different_bodies_produce_different_signatures() {
        Instant now = Instant.now();

        RequestSigner.SignedHeaders h1 = signer.sign("POST", "/peers", now, "body1".getBytes(StandardCharsets.UTF_8));
        RequestSigner.SignedHeaders h2 = signer.sign("POST", "/peers", now, "body2".getBytes(StandardCharsets.UTF_8));

        assertNotEquals(h1.signature(), h2.signature());
    }

    @Test
    void empty_body_is_handled() {
        RequestSigner.SignedHeaders headers = signer.sign("GET", "/info", new byte[0]);
        assertNotNull(headers.signature());
    }

    @Test
    void null_body_treated_as_empty() {
        RequestSigner.SignedHeaders headers = signer.sign("GET", "/info", Instant.now(), null);
        assertNotNull(headers.signature());
    }

    @Test
    void hash_bytes_deterministic() {
        byte[] data = "test".getBytes(StandardCharsets.UTF_8);
        assertEquals(RequestSigner.hashBytes(data), RequestSigner.hashBytes(data));
        assertEquals(64, RequestSigner.hashBytes(data).length());
    }
}
