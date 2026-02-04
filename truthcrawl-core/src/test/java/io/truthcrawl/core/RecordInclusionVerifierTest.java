package io.truthcrawl.core;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RecordInclusionVerifierTest {

    private static PublisherKey nodeKey;
    private static NodeSigner signer;
    private static ObservationRecord signedRecord;
    private static BatchManifest manifest;
    private static BatchMetadata metadata;

    @BeforeAll
    static void setUp() {
        nodeKey = PublisherKey.generate();
        signer = NodeSigner.fromKeyPair(nodeKey);

        ObservationRecord unsigned = ObservationRecord.builder()
                .version("0.1")
                .observedAt(Instant.parse("2024-01-15T12:00:00Z"))
                .url("https://example.com")
                .finalUrl("https://example.com/")
                .statusCode(200)
                .fetchMs(100)
                .contentHash("b94d27b9934d3e08a52e52d7da7dabfac484efe37a5380ee9088f7ace2efcde9")
                .nodeId(signer.nodeId())
                .build();
        signedRecord = signer.sign(unsigned);

        // Build a manifest containing this record's hash plus two others
        String recordHash = signedRecord.recordHash();
        manifest = BatchManifest.of(List.of(
                recordHash,
                "ca978112ca1bbdcafac231b39a23dc4da786eff8147c4e72b9807785afee48bb",
                "3e23e8160039594a33894f6564e1b1348bbd7a0088d42c4acb73eeaed59c009d"));

        metadata = BatchMetadata.fromManifest("2024-01-15", manifest);
    }

    @Test
    void valid_record_passes_inclusion_check() {
        RecordInclusionVerifier.Result result = RecordInclusionVerifier.verify(
                signedRecord, nodeKey.publicKeyBase64(), manifest, metadata);
        assertTrue(result.valid(), "Errors: " + result.errors());
    }

    @Test
    void wrong_node_key_fails() {
        PublisherKey wrongKey = PublisherKey.generate();
        RecordInclusionVerifier.Result result = RecordInclusionVerifier.verify(
                signedRecord, wrongKey.publicKeyBase64(), manifest, metadata);
        assertFalse(result.valid());
        assertTrue(result.errors().stream().anyMatch(e -> e.contains("signature")));
    }

    @Test
    void record_not_in_manifest_fails() {
        // Manifest without the record hash
        BatchManifest otherManifest = BatchManifest.of(List.of(
                "ca978112ca1bbdcafac231b39a23dc4da786eff8147c4e72b9807785afee48bb",
                "3e23e8160039594a33894f6564e1b1348bbd7a0088d42c4acb73eeaed59c009d"));
        BatchMetadata otherMetadata = BatchMetadata.fromManifest("2024-01-15", otherManifest);

        RecordInclusionVerifier.Result result = RecordInclusionVerifier.verify(
                signedRecord, nodeKey.publicKeyBase64(), otherManifest, otherMetadata);
        assertFalse(result.valid());
        assertTrue(result.errors().stream().anyMatch(e -> e.contains("not found")));
    }
}
