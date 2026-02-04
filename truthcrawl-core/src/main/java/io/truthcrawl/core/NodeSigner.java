package io.truthcrawl.core;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;

/**
 * Signs and verifies ObservationRecords using an Ed25519 key pair.
 *
 * <p>The node_id is the SHA-256 fingerprint of the public key (lowercase hex).
 * The signature covers the canonical text of the record (which excludes the signature field).
 */
public final class NodeSigner {

    private final PublisherKey key;
    private final String nodeId;

    private NodeSigner(PublisherKey key, String nodeId) {
        this.key = key;
        this.nodeId = nodeId;
    }

    /**
     * Create a signing-capable NodeSigner from a full key pair.
     */
    public static NodeSigner fromKeyPair(PublisherKey key) {
        return new NodeSigner(key, computeNodeId(key.publicKeyBase64()));
    }

    /**
     * Create a verify-only NodeSigner from a public key.
     */
    public static NodeSigner fromPublicKey(String publicKeyBase64) {
        PublisherKey key = PublisherKey.fromPublicKey(publicKeyBase64);
        return new NodeSigner(key, computeNodeId(publicKeyBase64));
    }

    /**
     * The node_id (SHA-256 fingerprint of the public key, lowercase hex).
     */
    public String nodeId() {
        return nodeId;
    }

    /**
     * Sign a record. Returns a new record with node_id and signature set.
     *
     * @param record the unsigned record (node_id must match this signer)
     * @return a new record with the signature attached
     */
    public ObservationRecord sign(ObservationRecord record) {
        if (!record.nodeId().equals(nodeId)) {
            throw new IllegalArgumentException(
                    "Record node_id " + record.nodeId() + " does not match signer " + nodeId);
        }
        byte[] canonical = record.toCanonicalText().getBytes(StandardCharsets.UTF_8);
        String signature = key.sign(canonical);
        return record.withSignature(signature);
    }

    /**
     * Verify a signed record's signature.
     *
     * @param record the signed record
     * @return true if the signature is valid for the canonical text
     */
    public boolean verify(ObservationRecord record) {
        if (record.nodeSignature() == null) {
            return false;
        }
        if (!record.nodeId().equals(nodeId)) {
            return false;
        }
        byte[] canonical = record.toCanonicalText().getBytes(StandardCharsets.UTF_8);
        return key.verify(canonical, record.nodeSignature());
    }

    /**
     * Compute node_id from a Base64-encoded public key.
     * node_id = SHA-256(raw_public_key_bytes) as lowercase hex.
     */
    public static String computeNodeId(String publicKeyBase64) {
        try {
            byte[] pubBytes = Base64.getDecoder().decode(publicKeyBase64);
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return MerkleTree.encodeHex(digest.digest(pubBytes));
        } catch (NoSuchAlgorithmException e) {
            throw new AssertionError("SHA-256 must be available", e);
        }
    }
}
