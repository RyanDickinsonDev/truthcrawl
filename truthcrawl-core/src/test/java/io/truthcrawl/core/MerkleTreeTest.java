package io.truthcrawl.core;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MerkleTreeTest {

    private static final Path VECTORS = Path.of("src/test/resources/vectors");

    /**
     * Root must match the independently-computed golden vector.
     */
    @Test
    void root_matches_expected_for_manifest() throws IOException {
        List<String> leaves = readManifest("manifest-3.txt");
        String expectedRoot = readExpectedRoot("expected-root-3.txt");

        String actualRoot = MerkleTree.computeRoot(leaves);

        assertEquals(expectedRoot, actualRoot);
    }

    /**
     * Changing any leaf must change the root.
     */
    @Test
    void root_changes_if_leaf_changes() throws IOException {
        List<String> original = readManifest("manifest-3.txt");
        String originalRoot = MerkleTree.computeRoot(original);

        // Flip last byte of first leaf
        String altered = original.get(0).substring(0, 62) + "00";
        List<String> modified = List.of(altered, original.get(1), original.get(2));
        String modifiedRoot = MerkleTree.computeRoot(modified);

        assertNotEquals(originalRoot, modifiedRoot);
    }

    /**
     * A single leaf's root is the leaf itself (no re-hashing).
     */
    @Test
    void handles_single_leaf() {
        String leaf = "ca978112ca1bbdcafac231b39a23dc4da786eff8147c4e72b9807785afee48bb";

        String root = MerkleTree.computeRoot(List.of(leaf));

        assertEquals(leaf, root);
    }

    /**
     * Odd-count levels must duplicate the last node before pairing.
     */
    @Test
    void duplicates_last_node_on_odd_level() {
        // With 3 leaves, level 0 is padded to 4 by duplicating the third leaf.
        // The root must differ from a 2-leaf tree of just the first two leaves.
        String l0 = "ca978112ca1bbdcafac231b39a23dc4da786eff8147c4e72b9807785afee48bb";
        String l1 = "3e23e8160039594a33894f6564e1b1348bbd7a0088d42c4acb73eeaed59c009d";
        String l2 = "2e7d2c03a9507ae265ecf5b5356885a53393a2029d241394997265a1a25aefc6";

        String root2 = MerkleTree.computeRoot(List.of(l0, l1));
        String root3 = MerkleTree.computeRoot(List.of(l0, l1, l2));

        assertNotEquals(root2, root3, "3-leaf root must differ from 2-leaf root");
    }

    /**
     * Invalid hex must be rejected immediately.
     */
    @Test
    void throws_on_invalid_hex_input() {
        // Too short
        assertThrows(IllegalArgumentException.class,
                () -> MerkleTree.computeRoot(List.of("abcd")));

        // Non-hex character
        assertThrows(IllegalArgumentException.class,
                () -> MerkleTree.computeRoot(List.of(
                        "zz78112ca1bbdcafac231b39a23dc4da786eff8147c4e72b9807785afee48bb")));

        // Empty list
        assertThrows(IllegalArgumentException.class,
                () -> MerkleTree.computeRoot(List.of()));
    }

    private List<String> readManifest(String filename) throws IOException {
        return Files.readAllLines(VECTORS.resolve(filename), StandardCharsets.UTF_8)
                .stream()
                .filter(line -> !line.isBlank())
                .toList();
    }

    private String readExpectedRoot(String filename) throws IOException {
        return Files.readString(VECTORS.resolve(filename), StandardCharsets.UTF_8).strip();
    }
}
