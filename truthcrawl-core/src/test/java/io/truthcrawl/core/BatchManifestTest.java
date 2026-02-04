package io.truthcrawl.core;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class BatchManifestTest {

    private static final String H_A = "ca978112ca1bbdcafac231b39a23dc4da786eff8147c4e72b9807785afee48bb";
    private static final String H_B = "3e23e8160039594a33894f6564e1b1348bbd7a0088d42c4acb73eeaed59c009d";
    private static final String H_C = "2e7d2c03a9507ae265ecf5b5356885a53393a2029d241394997265a1a25aefc6";

    // Golden vectors (independently computed in Python, sorted order)
    private static final String EXPECTED_MANIFEST_HASH =
            "a8a97548991324f0f374f85a5c9ec0d73518791961724892bb71c886efdd5458";
    private static final String EXPECTED_MERKLE_ROOT =
            "ca4d6f43563a356ecda2e7aa848c173a1b76209fa09c7dab27b6d4b1e27332e1";

    @Test
    void entries_are_sorted_and_deduped() {
        BatchManifest m = BatchManifest.of(List.of(H_A, H_B, H_C, H_A));
        assertEquals(3, m.size());
        assertEquals(List.of(H_C, H_B, H_A), m.entries()); // lexicographic order
    }

    @Test
    void canonical_text_is_sorted_newline_terminated() {
        BatchManifest m = BatchManifest.of(List.of(H_A, H_B, H_C));
        String expected = H_C + "\n" + H_B + "\n" + H_A + "\n";
        assertEquals(expected, m.toCanonicalText());
    }

    @Test
    void manifest_hash_matches_golden_vector() {
        BatchManifest m = BatchManifest.of(List.of(H_A, H_B, H_C));
        assertEquals(EXPECTED_MANIFEST_HASH, m.manifestHash());
    }

    @Test
    void merkle_root_matches_golden_vector() {
        BatchManifest m = BatchManifest.of(List.of(H_A, H_B, H_C));
        assertEquals(EXPECTED_MERKLE_ROOT, m.merkleRoot());
    }

    @Test
    void parse_round_trips_with_canonical_text() {
        BatchManifest original = BatchManifest.of(List.of(H_A, H_B, H_C));
        List<String> lines = List.of(original.toCanonicalText().split("\n"));
        BatchManifest parsed = BatchManifest.parse(lines);

        assertEquals(original.entries(), parsed.entries());
        assertEquals(original.manifestHash(), parsed.manifestHash());
    }

    @Test
    void throws_on_empty_input() {
        assertThrows(IllegalArgumentException.class, () -> BatchManifest.of(List.of()));
    }

    @Test
    void throws_on_invalid_hex() {
        assertThrows(IllegalArgumentException.class,
                () -> BatchManifest.of(List.of("not-a-hash")));
    }
}
