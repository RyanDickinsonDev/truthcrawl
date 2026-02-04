package io.truthcrawl.core;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class BatchMetadataTest {

    private static final String BATCH_ID = "2024-01-15";
    private static final String MERKLE_ROOT =
            "ca4d6f43563a356ecda2e7aa848c173a1b76209fa09c7dab27b6d4b1e27332e1";
    private static final String MANIFEST_HASH =
            "a8a97548991324f0f374f85a5c9ec0d73518791961724892bb71c886efdd5458";

    @Test
    void canonical_text_has_fixed_key_order() {
        BatchMetadata md = new BatchMetadata(BATCH_ID, MERKLE_ROOT, MANIFEST_HASH, 3);
        String expected = "batch_id:2024-01-15\n"
                + "merkle_root:" + MERKLE_ROOT + "\n"
                + "manifest_hash:" + MANIFEST_HASH + "\n"
                + "record_count:3\n";
        assertEquals(expected, md.toCanonicalText());
    }

    @Test
    void signing_input_has_version_prefix() {
        BatchMetadata md = new BatchMetadata(BATCH_ID, MERKLE_ROOT, MANIFEST_HASH, 3);
        String expected = "truthcrawl-batch-v1\n"
                + BATCH_ID + "\n"
                + MERKLE_ROOT + "\n"
                + MANIFEST_HASH + "\n"
                + "3\n";
        assertArrayEquals(expected.getBytes(StandardCharsets.UTF_8), md.signingInput());
    }

    @Test
    void from_manifest_derives_all_fields() {
        BatchManifest manifest = BatchManifest.of(List.of(
                "ca978112ca1bbdcafac231b39a23dc4da786eff8147c4e72b9807785afee48bb",
                "3e23e8160039594a33894f6564e1b1348bbd7a0088d42c4acb73eeaed59c009d",
                "2e7d2c03a9507ae265ecf5b5356885a53393a2029d241394997265a1a25aefc6"));
        BatchMetadata md = BatchMetadata.fromManifest(BATCH_ID, manifest);

        assertEquals(BATCH_ID, md.batchId());
        assertEquals(MERKLE_ROOT, md.merkleRoot());
        assertEquals(MANIFEST_HASH, md.manifestHash());
        assertEquals(3, md.recordCount());
    }

    @Test
    void parse_round_trips_with_canonical_text() {
        BatchMetadata original = new BatchMetadata(BATCH_ID, MERKLE_ROOT, MANIFEST_HASH, 3);
        List<String> lines = List.of(original.toCanonicalText().split("\n"));
        BatchMetadata parsed = BatchMetadata.parse(lines);

        assertEquals(original, parsed);
    }

    @Test
    void throws_on_invalid_batch_id() {
        assertThrows(IllegalArgumentException.class,
                () -> new BatchMetadata("not-a-date", MERKLE_ROOT, MANIFEST_HASH, 3));
    }

    @Test
    void throws_on_zero_record_count() {
        assertThrows(IllegalArgumentException.class,
                () -> new BatchMetadata(BATCH_ID, MERKLE_ROOT, MANIFEST_HASH, 0));
    }
}
