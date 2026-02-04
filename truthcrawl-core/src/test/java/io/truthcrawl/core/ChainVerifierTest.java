package io.truthcrawl.core;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChainVerifierTest {

    private static PublisherKey key;
    private static BatchManifest manifest1;
    private static BatchManifest manifest2;
    private static ChainLink link1;
    private static ChainLink link2;
    private static String sig1;
    private static String sig2;

    @BeforeAll
    static void setup() {
        key = PublisherKey.generate();

        manifest1 = BatchManifest.of(List.of(
                "ca978112ca1bbdcafac231b39a23dc4da786eff8147c4e72b9807785afee48bb",
                "3e23e8160039594a33894f6564e1b1348bbd7a0088d42c4acb73eeaed59c009d"
        ));
        link1 = ChainLink.fromManifest("2024-01-15", manifest1, ChainLink.GENESIS_ROOT);
        sig1 = key.sign(link1.signingInput());

        manifest2 = BatchManifest.of(List.of(
                "2e7d2c03a9507ae265ecf5b5356885a53393a2029d241394997265a1a25aefc6",
                "18ac3e7343f016890c510e93f935261169d9e3f565436429830faf0934f4f8e4"
        ));
        link2 = ChainLink.fromManifest("2024-01-16", manifest2, link1.merkleRoot());
        sig2 = key.sign(link2.signingInput());
    }

    @Test
    void valid_chain_passes() {
        ChainVerifier.Result result = ChainVerifier.verify(
                List.of(link1, link2),
                List.of(manifest1, manifest2),
                List.of(sig1, sig2),
                key);
        assertTrue(result.valid());
        assertTrue(result.errors().isEmpty());
    }

    @Test
    void single_genesis_passes() {
        ChainVerifier.Result result = ChainVerifier.verify(
                List.of(link1),
                List.of(manifest1),
                List.of(sig1),
                key);
        assertTrue(result.valid());
    }

    @Test
    void wrong_signature_detected() {
        ChainVerifier.Result result = ChainVerifier.verify(
                List.of(link1),
                List.of(manifest1),
                List.of("badsig=="),
                key);
        assertFalse(result.valid());
        assertTrue(result.errors().stream().anyMatch(e -> e.contains("invalid signature")));
    }

    @Test
    void wrong_key_detected() {
        PublisherKey otherKey = PublisherKey.generate();
        ChainVerifier.Result result = ChainVerifier.verify(
                List.of(link1),
                List.of(manifest1),
                List.of(sig1),
                otherKey);
        assertFalse(result.valid());
    }

    @Test
    void broken_chain_detected() {
        // link2 points to link1.merkleRoot, but we skip link1
        ChainVerifier.Result result = ChainVerifier.verify(
                List.of(link2),
                List.of(manifest2),
                List.of(sig2),
                key);
        assertFalse(result.valid());
        assertTrue(result.errors().stream().anyMatch(e -> e.contains("not genesis")));
    }

    @Test
    void tampered_manifest_detected() {
        // Give manifest2 for link1 (wrong manifest)
        ChainVerifier.Result result = ChainVerifier.verify(
                List.of(link1),
                List.of(manifest2),
                List.of(sig1),
                key);
        assertFalse(result.valid());
        assertTrue(result.errors().stream().anyMatch(e -> e.contains("manifest_hash mismatch")));
    }

    @Test
    void mismatched_counts_rejected() {
        ChainVerifier.Result result = ChainVerifier.verify(
                List.of(link1, link2),
                List.of(manifest1),
                List.of(sig1, sig2),
                key);
        assertFalse(result.valid());
        assertTrue(result.errors().stream().anyMatch(e -> e.contains("Mismatched counts")));
    }

    @Test
    void multiple_errors_all_reported() {
        PublisherKey otherKey = PublisherKey.generate();
        ChainVerifier.Result result = ChainVerifier.verify(
                List.of(link1),
                List.of(manifest2),  // wrong manifest
                List.of(sig1),       // signed with different key
                otherKey);
        assertFalse(result.valid());
        assertTrue(result.errors().size() >= 2);
    }
}
