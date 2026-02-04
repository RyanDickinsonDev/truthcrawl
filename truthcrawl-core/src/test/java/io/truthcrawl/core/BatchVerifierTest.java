package io.truthcrawl.core;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BatchVerifierTest {

    private static final String BATCH_ID = "2024-01-15";

    private static BatchManifest manifest;
    private static BatchMetadata metadata;
    private static PublisherKey key;
    private static String signature;

    @BeforeAll
    static void setUp() {
        manifest = BatchManifest.of(List.of(
                "ca978112ca1bbdcafac231b39a23dc4da786eff8147c4e72b9807785afee48bb",
                "3e23e8160039594a33894f6564e1b1348bbd7a0088d42c4acb73eeaed59c009d",
                "2e7d2c03a9507ae265ecf5b5356885a53393a2029d241394997265a1a25aefc6"));
        metadata = BatchMetadata.fromManifest(BATCH_ID, manifest);
        key = PublisherKey.generate();
        signature = key.sign(metadata.signingInput());
    }

    @Test
    void valid_batch_passes() {
        BatchVerifier.Result result = BatchVerifier.verify(metadata, manifest, signature, key);
        assertTrue(result.valid());
        assertTrue(result.errors().isEmpty());
    }

    @Test
    void wrong_signature_fails() {
        PublisherKey otherKey = PublisherKey.generate();
        String wrongSig = otherKey.sign(metadata.signingInput());

        BatchVerifier.Result result = BatchVerifier.verify(metadata, manifest, wrongSig, key);
        assertFalse(result.valid());
        assertTrue(result.errors().stream().anyMatch(e -> e.contains("Invalid signature")));
    }

    @Test
    void tampered_manifest_fails_hash_and_root() {
        // Different manifest with an extra entry
        BatchManifest tampered = BatchManifest.of(List.of(
                "ca978112ca1bbdcafac231b39a23dc4da786eff8147c4e72b9807785afee48bb",
                "3e23e8160039594a33894f6564e1b1348bbd7a0088d42c4acb73eeaed59c009d"));

        BatchVerifier.Result result = BatchVerifier.verify(metadata, tampered, signature, key);
        assertFalse(result.valid());
        assertTrue(result.errors().stream().anyMatch(e -> e.contains("manifest_hash")));
        assertTrue(result.errors().stream().anyMatch(e -> e.contains("merkle_root")));
    }

    @Test
    void wrong_record_count_fails() {
        // Metadata with wrong record count
        BatchMetadata badCount = new BatchMetadata(
                BATCH_ID, metadata.merkleRoot(), metadata.manifestHash(), 99);
        String badSig = key.sign(badCount.signingInput());

        BatchVerifier.Result result = BatchVerifier.verify(badCount, manifest, badSig, key);
        assertFalse(result.valid());
        assertTrue(result.errors().stream().anyMatch(e -> e.contains("record_count")));
    }

    @Test
    void multiple_errors_are_all_reported() {
        PublisherKey otherKey = PublisherKey.generate();
        BatchManifest tampered = BatchManifest.of(List.of(
                "0000000000000000000000000000000000000000000000000000000000000000"));

        // Everything is wrong: signature, manifest hash, root, count
        BatchVerifier.Result result = BatchVerifier.verify(metadata, tampered, signature, otherKey);
        assertFalse(result.valid());
        assertEquals(4, result.errors().size());
    }
}
