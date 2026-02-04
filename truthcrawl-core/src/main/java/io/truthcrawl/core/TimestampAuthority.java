package io.truthcrawl.core;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;

/**
 * A local Timestamp Authority that issues {@link TimestampToken}s using Ed25519.
 *
 * <p>The TSA is a separate key pair from publisher and node keys. It signs a
 * versioned prefix + data_hash + issued_at + tsa_key_id to produce a token
 * that attests "this hash existed at this time."
 *
 * <p>The TSA does not need to know what the data hash represents.
 */
public final class TimestampAuthority {

    private final PublisherKey key;
    private final String tsaKeyId;

    /**
     * Create a timestamp authority with the given key pair.
     *
     * @param key the TSA's Ed25519 key pair (must have private key for signing)
     */
    public TimestampAuthority(PublisherKey key) {
        this.key = key;
        this.tsaKeyId = computeKeyId(key);
    }

    /**
     * The TSA's key ID (SHA-256 fingerprint of the public key, lowercase hex).
     */
    public String tsaKeyId() {
        return tsaKeyId;
    }

    /**
     * Issue a timestamp token for the given data hash.
     *
     * @param dataHash 64-char lowercase hex SHA-256 of the data to timestamp
     * @param issuedAt the timestamp to bind (UTC)
     * @return a signed timestamp token
     */
    public TimestampToken issue(String dataHash, Instant issuedAt) {
        // Build a token without signature first to get the signing input
        // We need the signing input bytes, which depend on dataHash, issuedAt, tsaKeyId
        TimestampToken unsigned = new TimestampToken(dataHash, issuedAt, tsaKeyId, "placeholder");
        byte[] signingInput = unsigned.signingInput();
        String signature = key.sign(signingInput);

        return new TimestampToken(dataHash, issuedAt, tsaKeyId, signature);
    }

    /**
     * Issue a timestamp token for the given data hash at the current time.
     *
     * @param dataHash 64-char lowercase hex SHA-256 of the data to timestamp
     * @return a signed timestamp token
     */
    public TimestampToken issue(String dataHash) {
        return issue(dataHash, Instant.now());
    }

    /**
     * Compute the key ID (SHA-256 of the Base64-encoded public key).
     */
    static String computeKeyId(PublisherKey key) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(
                    key.publicKeyBase64().getBytes(StandardCharsets.UTF_8));
            return MerkleTree.encodeHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new AssertionError("SHA-256 must be available", e);
        }
    }
}
