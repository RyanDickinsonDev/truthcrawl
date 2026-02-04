package io.truthcrawl.core;

import java.util.List;

/**
 * Verifies that an ObservationRecord is included in a signed batch.
 *
 * <p>Verification chain:
 * <ol>
 *   <li>Verify the record's node signature.</li>
 *   <li>Compute the record hash.</li>
 *   <li>Find the record hash in the batch manifest.</li>
 *   <li>Compute the Merkle inclusion proof.</li>
 *   <li>Verify the proof against the batch's Merkle root.</li>
 * </ol>
 */
public final class RecordInclusionVerifier {

    private RecordInclusionVerifier() {}

    /**
     * Result of record inclusion verification.
     *
     * @param valid  true if all checks passed
     * @param errors failure descriptions (empty if valid)
     */
    public record Result(boolean valid, List<String> errors) {
        public static Result ok() {
            return new Result(true, List.of());
        }

        public static Result fail(List<String> errors) {
            return new Result(false, List.copyOf(errors));
        }
    }

    /**
     * Verify that a signed record is included in a batch.
     *
     * @param record         the signed ObservationRecord
     * @param nodePublicKey  Base64-encoded Ed25519 public key of the node
     * @param manifest       the batch manifest
     * @param batchMetadata  the batch metadata
     * @return verification result
     */
    public static Result verify(
            ObservationRecord record,
            String nodePublicKey,
            BatchManifest manifest,
            BatchMetadata batchMetadata) {

        java.util.ArrayList<String> errors = new java.util.ArrayList<>();

        // 1. Verify node signature
        NodeSigner verifier = NodeSigner.fromPublicKey(nodePublicKey);
        if (!verifier.verify(record)) {
            errors.add("Invalid node signature");
        }

        // 2. Compute record hash
        String recordHash = record.recordHash();

        // 3. Find in manifest
        int leafIndex = manifest.entries().indexOf(recordHash);
        if (leafIndex == -1) {
            errors.add("Record hash " + recordHash + " not found in manifest");
            return Result.fail(errors);
        }

        // 4. Compute and verify inclusion proof
        List<ProofStep> proof = MerkleTree.computeProof(manifest.entries(), leafIndex);
        if (!MerkleTree.verifyProof(recordHash, proof, batchMetadata.merkleRoot())) {
            errors.add("Merkle inclusion proof failed");
        }

        // 5. Verify manifest hash matches metadata
        if (!manifest.manifestHash().equals(batchMetadata.manifestHash())) {
            errors.add("Manifest hash mismatch");
        }

        if (errors.isEmpty()) {
            return Result.ok();
        }
        return Result.fail(errors);
    }
}
