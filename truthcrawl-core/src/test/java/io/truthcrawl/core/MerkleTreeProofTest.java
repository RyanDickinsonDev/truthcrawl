package io.truthcrawl.core;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MerkleTreeProofTest {

    private static final String L0 = "ca978112ca1bbdcafac231b39a23dc4da786eff8147c4e72b9807785afee48bb";
    private static final String L1 = "3e23e8160039594a33894f6564e1b1348bbd7a0088d42c4acb73eeaed59c009d";
    private static final String L2 = "2e7d2c03a9507ae265ecf5b5356885a53393a2029d241394997265a1a25aefc6";
    private static final List<String> LEAVES = List.of(L0, L1, L2);

    private static final String ROOT = "d31a37ef6ac14a2db1470c4316beb5592e6afd4465022339adafda76a18ffabe";

    /**
     * Proof for leaf 0 must verify against the known root.
     * Expected steps (from Python reference):
     *   right:L1, right:SHA256(L2||L2)
     */
    @Test
    void proof_for_leaf_0_verifies() {
        List<ProofStep> proof = MerkleTree.computeProof(LEAVES, 0);
        assertTrue(MerkleTree.verifyProof(L0, proof, ROOT));
    }

    /**
     * Proof for leaf 2 must verify against the known root.
     * Expected steps (from Python reference):
     *   right:L2 (dup), left:SHA256(L0||L1)
     */
    @Test
    void proof_for_leaf_2_verifies() {
        List<ProofStep> proof = MerkleTree.computeProof(LEAVES, 2);
        assertTrue(MerkleTree.verifyProof(L2, proof, ROOT));
    }

    /**
     * Proof for every leaf in the manifest must verify.
     */
    @Test
    void proof_verifies_for_all_leaves() {
        for (int i = 0; i < LEAVES.size(); i++) {
            List<ProofStep> proof = MerkleTree.computeProof(LEAVES, i);
            assertTrue(MerkleTree.verifyProof(LEAVES.get(i), proof, ROOT),
                    "Proof failed for leaf " + i);
        }
    }

    /**
     * Proof steps must match the independently-computed golden vector.
     */
    @Test
    void proof_steps_match_golden_vector_for_leaf_0() {
        List<ProofStep> proof = MerkleTree.computeProof(LEAVES, 0);

        assertEquals(2, proof.size());

        // Step 0: sibling is L1, on the right
        assertEquals(L1, MerkleTree.encodeHex(proof.get(0).siblingHash()));
        assertEquals(ProofStep.Position.RIGHT, proof.get(0).position());

        // Step 1: sibling is SHA256(L2||L2), on the right
        assertEquals("a3e333fbee455b9a054cf05077f0f9d45b91bd13db4cd4a3681ec47455af085c",
                MerkleTree.encodeHex(proof.get(1).siblingHash()));
        assertEquals(ProofStep.Position.RIGHT, proof.get(1).position());
    }

    /**
     * Proof against a wrong root must fail.
     */
    @Test
    void proof_rejects_wrong_root() {
        List<ProofStep> proof = MerkleTree.computeProof(LEAVES, 0);
        String wrongRoot = "0000000000000000000000000000000000000000000000000000000000000000";
        assertFalse(MerkleTree.verifyProof(L0, proof, wrongRoot));
    }

    /**
     * Proof with a tampered leaf must fail.
     */
    @Test
    void proof_rejects_wrong_leaf() {
        List<ProofStep> proof = MerkleTree.computeProof(LEAVES, 0);
        assertFalse(MerkleTree.verifyProof(L1, proof, ROOT));
    }

    /**
     * Single-leaf tree has an empty proof that verifies.
     */
    @Test
    void single_leaf_proof_is_empty_and_verifies() {
        List<ProofStep> proof = MerkleTree.computeProof(List.of(L0), 0);
        assertTrue(proof.isEmpty());
        assertTrue(MerkleTree.verifyProof(L0, proof, L0));
    }

    /**
     * Out-of-range index must throw.
     */
    @Test
    void throws_on_invalid_leaf_index() {
        assertThrows(IllegalArgumentException.class,
                () -> MerkleTree.computeProof(LEAVES, -1));
        assertThrows(IllegalArgumentException.class,
                () -> MerkleTree.computeProof(LEAVES, 3));
    }
}
