package io.truthcrawl.core;

import java.util.ArrayList;
import java.util.List;

/**
 * Verifies the integrity of an entire batch chain.
 *
 * <p>Verification checks per link:
 * <ol>
 *   <li>Signature over the chain link signing input is valid.</li>
 *   <li>manifest_hash matches SHA-256 of the manifest canonical text.</li>
 *   <li>merkle_root matches the Merkle root computed from manifest entries.</li>
 *   <li>record_count matches the manifest entry count.</li>
 * </ol>
 *
 * <p>Chain-level checks:
 * <ol>
 *   <li>First link is genesis (previous_root = 64 zeros).</li>
 *   <li>Each subsequent link's previous_root equals the prior link's merkle_root.</li>
 * </ol>
 */
public final class ChainVerifier {

    private ChainVerifier() {}

    /**
     * Result of chain verification.
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
     * Verify a signed batch chain.
     *
     * @param links        ordered chain links (genesis first)
     * @param manifests    corresponding manifests (one per link, same order)
     * @param signatures   corresponding signatures (one per link, same order)
     * @param publisherKey the publisher's public key
     * @return verification result
     */
    public static Result verify(
            List<ChainLink> links,
            List<BatchManifest> manifests,
            List<String> signatures,
            PublisherKey publisherKey) {

        if (links.size() != manifests.size() || links.size() != signatures.size()) {
            return Result.fail(List.of(
                    "Mismatched counts: " + links.size() + " links, "
                            + manifests.size() + " manifests, "
                            + signatures.size() + " signatures"));
        }

        if (links.isEmpty()) {
            return Result.fail(List.of("Chain must not be empty"));
        }

        List<String> errors = new ArrayList<>();

        // Chain-level: genesis check
        if (!links.get(0).isGenesis()) {
            errors.add("Link 0: not genesis, previous_root=" + links.get(0).previousRoot());
        }

        // Chain-level: continuity check
        for (int i = 1; i < links.size(); i++) {
            String expected = links.get(i - 1).merkleRoot();
            String actual = links.get(i).previousRoot();
            if (!actual.equals(expected)) {
                errors.add("Link " + i + ": chain break, previous_root=" + actual
                        + " expected=" + expected);
            }
        }

        // Per-link verification
        for (int i = 0; i < links.size(); i++) {
            ChainLink link = links.get(i);
            BatchManifest manifest = manifests.get(i);
            String signature = signatures.get(i);
            String prefix = "Link " + i + " (" + link.batchId() + "): ";

            // 1. Verify signature over chain link signing input
            if (!publisherKey.verify(link.signingInput(), signature)) {
                errors.add(prefix + "invalid signature");
            }

            // 2. Verify manifest hash
            String computedManifestHash = manifest.manifestHash();
            if (!computedManifestHash.equals(link.manifestHash())) {
                errors.add(prefix + "manifest_hash mismatch: link="
                        + link.manifestHash() + " computed=" + computedManifestHash);
            }

            // 3. Verify Merkle root
            String computedRoot = manifest.merkleRoot();
            if (!computedRoot.equals(link.merkleRoot())) {
                errors.add(prefix + "merkle_root mismatch: link="
                        + link.merkleRoot() + " computed=" + computedRoot);
            }

            // 4. Verify record count
            if (manifest.size() != link.recordCount()) {
                errors.add(prefix + "record_count mismatch: link="
                        + link.recordCount() + " manifest=" + manifest.size());
            }
        }

        if (errors.isEmpty()) {
            return Result.ok();
        }
        return Result.fail(errors);
    }
}
