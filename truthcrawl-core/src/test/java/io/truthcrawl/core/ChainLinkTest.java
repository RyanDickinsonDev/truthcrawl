package io.truthcrawl.core;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChainLinkTest {

    private static final String ROOT =
            "d31a37ef6ac14a2db1470c4316beb5592e6afd4465022339adafda76a18ffabe";
    private static final String MANIFEST_HASH =
            "a8a97548991324f0f374f85a5c9ec0d73518791961724892bb71c886efdd5458";

    @Test
    void canonical_text_has_fixed_field_order() {
        ChainLink link = new ChainLink("2024-01-15", ROOT, MANIFEST_HASH, 3,
                ChainLink.GENESIS_ROOT);
        String text = link.toCanonicalText();
        String[] lines = text.split("\n");
        assertEquals(5, lines.length);
        assertTrue(lines[0].startsWith("batch_id:"));
        assertTrue(lines[1].startsWith("merkle_root:"));
        assertTrue(lines[2].startsWith("manifest_hash:"));
        assertTrue(lines[3].startsWith("record_count:"));
        assertTrue(lines[4].startsWith("previous_root:"));
    }

    @Test
    void genesis_link_detected() {
        ChainLink genesis = new ChainLink("2024-01-15", ROOT, MANIFEST_HASH, 3,
                ChainLink.GENESIS_ROOT);
        assertTrue(genesis.isGenesis());

        ChainLink nonGenesis = new ChainLink("2024-01-16", ROOT, MANIFEST_HASH, 3, ROOT);
        assertFalse(nonGenesis.isGenesis());
    }

    @Test
    void link_hash_is_deterministic() {
        ChainLink link = new ChainLink("2024-01-15", ROOT, MANIFEST_HASH, 3,
                ChainLink.GENESIS_ROOT);
        assertEquals(link.linkHash(), link.linkHash());
        assertEquals(64, link.linkHash().length());
    }

    @Test
    void signing_input_has_chain_prefix() {
        ChainLink link = new ChainLink("2024-01-15", ROOT, MANIFEST_HASH, 3,
                ChainLink.GENESIS_ROOT);
        String signingText = new String(link.signingInput());
        assertTrue(signingText.startsWith("truthcrawl-chain-v1\n"));
    }

    @Test
    void signing_input_differs_from_batch_metadata() {
        ChainLink link = new ChainLink("2024-01-15", ROOT, MANIFEST_HASH, 3,
                ChainLink.GENESIS_ROOT);
        BatchMetadata metadata = new BatchMetadata("2024-01-15", ROOT, MANIFEST_HASH, 3);

        // Chain link signs with different prefix and includes previous_root
        String chainSigning = new String(link.signingInput());
        String batchSigning = new String(metadata.signingInput());
        assertFalse(chainSigning.equals(batchSigning));
    }

    @Test
    void parse_round_trip() {
        ChainLink original = new ChainLink("2024-01-15", ROOT, MANIFEST_HASH, 3,
                ChainLink.GENESIS_ROOT);
        ChainLink parsed = ChainLink.parse(List.of(original.toCanonicalText().split("\n")));

        assertEquals(original.batchId(), parsed.batchId());
        assertEquals(original.merkleRoot(), parsed.merkleRoot());
        assertEquals(original.manifestHash(), parsed.manifestHash());
        assertEquals(original.recordCount(), parsed.recordCount());
        assertEquals(original.previousRoot(), parsed.previousRoot());
    }

    @Test
    void from_manifest_computes_fields() {
        BatchManifest manifest = BatchManifest.of(List.of(
                "ca978112ca1bbdcafac231b39a23dc4da786eff8147c4e72b9807785afee48bb",
                "3e23e8160039594a33894f6564e1b1348bbd7a0088d42c4acb73eeaed59c009d",
                "2e7d2c03a9507ae265ecf5b5356885a53393a2029d241394997265a1a25aefc6"
        ));
        ChainLink link = ChainLink.fromManifest("2024-01-15", manifest, ChainLink.GENESIS_ROOT);

        assertEquals(manifest.merkleRoot(), link.merkleRoot());
        assertEquals(manifest.manifestHash(), link.manifestHash());
        assertEquals(3, link.recordCount());
    }

    @Test
    void rejects_invalid_batch_id() {
        assertThrows(IllegalArgumentException.class, () ->
                new ChainLink("bad", ROOT, MANIFEST_HASH, 3, ChainLink.GENESIS_ROOT));
    }

    @Test
    void rejects_invalid_previous_root() {
        assertThrows(IllegalArgumentException.class, () ->
                new ChainLink("2024-01-15", ROOT, MANIFEST_HASH, 3, "short"));
    }
}
