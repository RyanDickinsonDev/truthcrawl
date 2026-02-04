package io.truthcrawl.core;

import java.util.ArrayList;
import java.util.List;

/**
 * Verifies a signed transparency log batch.
 *
 * <p>Verification checks (all must pass):
 * <ol>
 *   <li>Signature over the canonical signing input is valid for the given public key.</li>
 *   <li>manifest_hash in metadata matches SHA-256 of the manifest canonical text.</li>
 *   <li>merkle_root in metadata matches the Merkle root computed from manifest entries.</li>
 *   <li>record_count in metadata matches the manifest entry count.</li>
 * </ol>
 *
 * <p>This verifier does not trust the publisher. All claims in the metadata are
 * independently recomputed from the manifest.
 */
public final class BatchVerifier {

    private BatchVerifier() {}

    /**
     * Result of batch verification.
     *
     * @param valid  true if all checks passed
     * @param errors list of failure descriptions (empty if valid)
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
     * Verify a signed batch.
     *
     * @param metadata        the batch metadata (as published)
     * @param manifest        the batch manifest (as published)
     * @param signature       Base64-encoded Ed25519 signature
     * @param publisherKey    verify-only PublisherKey
     * @return verification result
     */
    public static Result verify(
            BatchMetadata metadata,
            BatchManifest manifest,
            String signature,
            PublisherKey publisherKey) {

        List<String> errors = new ArrayList<>();

        // 1. Verify signature
        if (!publisherKey.verify(metadata.signingInput(), signature)) {
            errors.add("Invalid signature");
        }

        // 2. Verify manifest hash
        String computedManifestHash = manifest.manifestHash();
        if (!computedManifestHash.equals(metadata.manifestHash())) {
            errors.add("manifest_hash mismatch: metadata=" + metadata.manifestHash()
                    + " computed=" + computedManifestHash);
        }

        // 3. Verify Merkle root
        String computedRoot = manifest.merkleRoot();
        if (!computedRoot.equals(metadata.merkleRoot())) {
            errors.add("merkle_root mismatch: metadata=" + metadata.merkleRoot()
                    + " computed=" + computedRoot);
        }

        // 4. Verify record count
        if (manifest.size() != metadata.recordCount()) {
            errors.add("record_count mismatch: metadata=" + metadata.recordCount()
                    + " manifest=" + manifest.size());
        }

        if (errors.isEmpty()) {
            return Result.ok();
        }
        return Result.fail(errors);
    }
}
