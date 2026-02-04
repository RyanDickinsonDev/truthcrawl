package io.truthcrawl.core;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VerificationSamplerTest {

    private static final String ROOT =
            "d31a37ef6ac14a2db1470c4316beb5592e6afd4465022339adafda76a18ffabe";

    private BatchManifest manifest() {
        return BatchManifest.of(List.of(
                "ca978112ca1bbdcafac231b39a23dc4da786eff8147c4e72b9807785afee48bb",
                "3e23e8160039594a33894f6564e1b1348bbd7a0088d42c4acb73eeaed59c009d",
                "2e7d2c03a9507ae265ecf5b5356885a53393a2029d241394997265a1a25aefc6",
                "18ac3e7343f016890c510e93f935261169d9e3f565436429830faf0934f4f8e4",
                "3f79bb7b435b05321651daefd374cdc681dc06faa65e374e38337b88ca046dea"
        ));
    }

    @Test
    void deterministic_same_seed_same_result() {
        BatchManifest m = manifest();
        List<String> s1 = VerificationSampler.sample(m, ROOT, "audit-2024");
        List<String> s2 = VerificationSampler.sample(m, ROOT, "audit-2024");
        assertEquals(s1, s2);
    }

    @Test
    void different_seeds_different_results() {
        BatchManifest m = manifest();
        // Use sample size < manifest size so different seeds select different subsets
        List<String> s1 = VerificationSampler.sample(m, ROOT, "seed-a", 3);
        List<String> s2 = VerificationSampler.sample(m, ROOT, "seed-b", 3);
        assertNotEquals(s1, s2);
    }

    @Test
    void different_roots_different_results() {
        BatchManifest m = manifest();
        String otherRoot = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";
        List<String> s1 = VerificationSampler.sample(m, ROOT, "seed", 3);
        List<String> s2 = VerificationSampler.sample(m, otherRoot, "seed", 3);
        assertNotEquals(s1, s2);
    }

    @Test
    void sample_size_capped_at_manifest_size() {
        BatchManifest m = manifest();
        List<String> selected = VerificationSampler.sample(m, ROOT, "seed", 100);
        assertEquals(5, selected.size()); // manifest has 5 entries
    }

    @Test
    void default_sample_size_is_min_10_or_manifest() {
        BatchManifest m = manifest();
        List<String> selected = VerificationSampler.sample(m, ROOT, "seed");
        assertEquals(5, selected.size()); // min(10, 5) = 5
    }

    @Test
    void custom_sample_size() {
        BatchManifest m = manifest();
        List<String> selected = VerificationSampler.sample(m, ROOT, "seed", 2);
        assertEquals(2, selected.size());
    }

    @Test
    void selected_hashes_are_from_manifest() {
        BatchManifest m = manifest();
        List<String> selected = VerificationSampler.sample(m, ROOT, "seed", 3);
        for (String hash : selected) {
            assertTrue(m.entries().contains(hash),
                    "Selected hash not in manifest: " + hash);
        }
    }

    @Test
    void no_duplicates_in_selection() {
        BatchManifest m = manifest();
        List<String> selected = VerificationSampler.sample(m, ROOT, "seed", 5);
        assertEquals(selected.size(), selected.stream().distinct().count());
    }

    @Test
    void output_is_sorted() {
        BatchManifest m = manifest();
        List<String> selected = VerificationSampler.sample(m, ROOT, "seed", 4);
        for (int i = 1; i < selected.size(); i++) {
            assertTrue(selected.get(i).compareTo(selected.get(i - 1)) >= 0);
        }
    }

    @Test
    void rejects_zero_sample_size() {
        assertThrows(IllegalArgumentException.class, () ->
                VerificationSampler.sample(manifest(), ROOT, "seed", 0));
    }
}
