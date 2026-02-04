package io.truthcrawl.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Verifies {@link NodeProfile}s using only the embedded public key.
 *
 * <p>Verification checks for registration:
 * <ol>
 *   <li>Registration is parseable and has all required fields</li>
 *   <li>node_id matches SHA-256 fingerprint of the embedded public key</li>
 *   <li>registration_signature is valid over the signing input</li>
 * </ol>
 *
 * <p>Verification checks for attestation (if present):
 * <ol>
 *   <li>Attestation is parseable and has all required fields</li>
 *   <li>node_id matches the registration's node_id</li>
 *   <li>attestation_signature is valid over the signing input using the same public key</li>
 * </ol>
 *
 * <p>Verification requires only the profile text (the public key is embedded
 * in the registration). No external context is needed.
 */
public final class NodeProfileVerifier {

    private NodeProfileVerifier() {}

    /**
     * Verification result.
     *
     * @param valid  true if all checks passed
     * @param errors list of error messages (empty if valid)
     */
    public record Result(boolean valid, List<String> errors) {
        public static Result ok() {
            return new Result(true, List.of());
        }

        public static Result fail(List<String> errors) {
            return new Result(false, Collections.unmodifiableList(errors));
        }
    }

    /**
     * Verify a node profile.
     *
     * @param profile the profile to verify
     * @return verification result
     */
    public static Result verify(NodeProfile profile) {
        List<String> errors = new ArrayList<>();
        NodeRegistration reg = profile.registration();

        // Reconstruct the public key from the registration
        PublisherKey nodeKey;
        try {
            nodeKey = PublisherKey.fromPublicKey(reg.publicKey());
        } catch (IllegalArgumentException e) {
            errors.add("Invalid public key in registration: " + e.getMessage());
            return Result.fail(errors);
        }

        // Check node_id matches the public key fingerprint
        String expectedNodeId = RequestSigner.computeNodeId(nodeKey);
        if (!reg.nodeId().equals(expectedNodeId)) {
            errors.add("node_id mismatch: registration has " + reg.nodeId()
                    + " but public key computes " + expectedNodeId);
        }

        // Check registration signature
        boolean regSigValid = nodeKey.verify(reg.signingInput(), reg.registrationSignature());
        if (!regSigValid) {
            errors.add("Invalid registration signature");
        }

        // Check attestation if present
        CrawlAttestation att = profile.attestation();
        if (att != null) {
            if (!att.nodeId().equals(reg.nodeId())) {
                errors.add("attestation node_id (" + att.nodeId()
                        + ") does not match registration node_id (" + reg.nodeId() + ")");
            }

            boolean attSigValid = nodeKey.verify(att.signingInput(), att.attestationSignature());
            if (!attSigValid) {
                errors.add("Invalid attestation signature");
            }
        }

        if (errors.isEmpty()) {
            return Result.ok();
        }
        return Result.fail(errors);
    }
}
