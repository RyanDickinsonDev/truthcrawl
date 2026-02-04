package io.truthcrawl.core;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NodeSignerTest {

    private static PublisherKey key;
    private static NodeSigner signer;

    @BeforeAll
    static void setUp() {
        key = PublisherKey.generate();
        signer = NodeSigner.fromKeyPair(key);
    }

    private ObservationRecord buildUnsigned() {
        return ObservationRecord.builder()
                .version("0.1")
                .observedAt(Instant.parse("2024-01-15T12:00:00Z"))
                .url("https://example.com")
                .finalUrl("https://example.com/")
                .statusCode(200)
                .fetchMs(100)
                .contentHash("b94d27b9934d3e08a52e52d7da7dabfac484efe37a5380ee9088f7ace2efcde9")
                .nodeId(signer.nodeId())
                .build();
    }

    @Test
    void node_id_is_sha256_of_public_key() {
        String nodeId = signer.nodeId();
        assertEquals(64, nodeId.length());
        assertEquals(NodeSigner.computeNodeId(key.publicKeyBase64()), nodeId);
    }

    @Test
    void sign_and_verify_round_trip() {
        ObservationRecord unsigned = buildUnsigned();
        ObservationRecord signed = signer.sign(unsigned);

        assertNotNull(signed.nodeSignature());
        assertTrue(signer.verify(signed));
    }

    @Test
    void verify_rejects_tampered_record() {
        ObservationRecord signed = signer.sign(buildUnsigned());

        // Build a different record with the same signature
        ObservationRecord tampered = ObservationRecord.builder()
                .version("0.1")
                .observedAt(Instant.parse("2024-01-15T12:00:00Z"))
                .url("https://example.com")
                .finalUrl("https://example.com/TAMPERED")
                .statusCode(200)
                .fetchMs(100)
                .contentHash("b94d27b9934d3e08a52e52d7da7dabfac484efe37a5380ee9088f7ace2efcde9")
                .nodeId(signer.nodeId())
                .build()
                .withSignature(signed.nodeSignature());

        assertFalse(signer.verify(tampered));
    }

    @Test
    void verify_rejects_wrong_key() {
        ObservationRecord signed = signer.sign(buildUnsigned());

        PublisherKey otherKey = PublisherKey.generate();
        NodeSigner otherSigner = NodeSigner.fromKeyPair(otherKey);

        assertFalse(otherSigner.verify(signed));
    }

    @Test
    void verify_rejects_unsigned_record() {
        ObservationRecord unsigned = buildUnsigned();
        assertFalse(signer.verify(unsigned));
    }

    @Test
    void sign_rejects_mismatched_node_id() {
        ObservationRecord wrongId = ObservationRecord.builder()
                .version("0.1")
                .observedAt(Instant.parse("2024-01-15T12:00:00Z"))
                .url("https://example.com")
                .finalUrl("https://example.com/")
                .statusCode(200)
                .fetchMs(100)
                .contentHash("b94d27b9934d3e08a52e52d7da7dabfac484efe37a5380ee9088f7ace2efcde9")
                .nodeId("0000000000000000000000000000000000000000000000000000000000000000")
                .build();

        assertThrows(IllegalArgumentException.class, () -> signer.sign(wrongId));
    }

    @Test
    void public_key_only_can_verify() {
        ObservationRecord signed = signer.sign(buildUnsigned());

        NodeSigner verifier = NodeSigner.fromPublicKey(key.publicKeyBase64());
        assertTrue(verifier.verify(signed));
    }
}
