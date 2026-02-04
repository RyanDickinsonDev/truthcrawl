package io.truthcrawl.core;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.format.DateTimeFormatter;

/**
 * Signs HTTP requests with Ed25519 for authenticated peer communication.
 *
 * <p>The signing input is a versioned, deterministic byte sequence:
 * <pre>
 * truthcrawl-auth-v1\n
 * {HTTP method}\n
 * {request path}\n
 * {X-Timestamp value}\n
 * {SHA-256 of request body, lowercase hex}\n
 * </pre>
 *
 * <p>The signing prefix "truthcrawl-auth-v1" prevents cross-protocol signature reuse.
 */
public final class RequestSigner {

    private static final String SIGNING_PREFIX = "truthcrawl-auth-v1";
    private static final byte[] EMPTY_BODY = new byte[0];

    private final PublisherKey key;
    private final String nodeId;

    /**
     * Create a request signer with the given key pair.
     *
     * @param key the node's Ed25519 key pair (must have private key for signing)
     */
    public RequestSigner(PublisherKey key) {
        this.key = key;
        this.nodeId = computeNodeId(key);
    }

    /**
     * The signer's node ID (SHA-256 fingerprint of the public key, lowercase hex).
     */
    public String nodeId() {
        return nodeId;
    }

    /**
     * The signer's public key (Base64-encoded).
     */
    public String publicKeyBase64() {
        return key.publicKeyBase64();
    }

    /**
     * Sign a request.
     *
     * @param method    HTTP method (e.g. "GET", "POST")
     * @param path      request path (e.g. "/peers")
     * @param timestamp ISO-8601 UTC timestamp
     * @param body      request body bytes (empty array for no body)
     * @return signed request headers (nodeId, timestamp, signature)
     */
    public SignedHeaders sign(String method, String path, Instant timestamp, byte[] body) {
        String ts = DateTimeFormatter.ISO_INSTANT.format(timestamp);
        String bodyHash = hashBytes(body != null ? body : EMPTY_BODY);
        byte[] signingInput = buildSigningInput(method, path, ts, bodyHash);
        String signature = key.sign(signingInput);
        return new SignedHeaders(nodeId, ts, signature);
    }

    /**
     * Sign a request at the current time.
     */
    public SignedHeaders sign(String method, String path, byte[] body) {
        return sign(method, path, Instant.now(), body);
    }

    /**
     * Build the signing input bytes.
     */
    public static byte[] buildSigningInput(String method, String path, String timestamp, String bodyHash) {
        String input = SIGNING_PREFIX + "\n"
                + method + "\n"
                + path + "\n"
                + timestamp + "\n"
                + bodyHash + "\n";
        return input.getBytes(StandardCharsets.UTF_8);
    }

    /**
     * Compute SHA-256 hash of bytes, returned as lowercase hex.
     */
    public static String hashBytes(byte[] data) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(data);
            return MerkleTree.encodeHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new AssertionError("SHA-256 must be available", e);
        }
    }

    /**
     * Compute the node ID (SHA-256 of the Base64-encoded public key).
     */
    public static String computeNodeId(PublisherKey key) {
        return hashBytes(key.publicKeyBase64().getBytes(StandardCharsets.UTF_8));
    }

    /**
     * The three authentication headers attached to a signed request.
     *
     * @param nodeId    X-Node-Id header value
     * @param timestamp X-Timestamp header value
     * @param signature X-Signature header value
     */
    public record SignedHeaders(String nodeId, String timestamp, String signature) {}
}
