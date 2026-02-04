package io.truthcrawl.core;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Verifies signed HTTP requests against a {@link PeerRegistry}.
 *
 * <p>Verification steps:
 * <ol>
 *   <li>Parse X-Node-Id, X-Timestamp, X-Signature headers</li>
 *   <li>Reject if any header is missing</li>
 *   <li>Reject if X-Timestamp is more than 5 minutes from server time</li>
 *   <li>Look up peer's public key by X-Node-Id in the peer registry</li>
 *   <li>Reject if peer is unknown</li>
 *   <li>Reconstruct signing input from request method, path, timestamp, body</li>
 *   <li>Verify X-Signature against signing input using peer's public key</li>
 * </ol>
 */
public final class RequestVerifier {

    private static final Duration MAX_CLOCK_SKEW = Duration.ofMinutes(5);

    private final PeerRegistry registry;

    /**
     * Create a request verifier backed by the given peer registry.
     *
     * @param registry the peer registry for looking up public keys
     */
    public RequestVerifier(PeerRegistry registry) {
        this.registry = registry;
    }

    /**
     * Verify a signed request.
     *
     * @param method    HTTP method (e.g. "POST")
     * @param path      request path (e.g. "/peers")
     * @param nodeId    X-Node-Id header value (may be null)
     * @param timestamp X-Timestamp header value (may be null)
     * @param signature X-Signature header value (may be null)
     * @param body      request body bytes
     * @param now       current server time
     * @return verification result
     */
    public Result verify(String method, String path,
                         String nodeId, String timestamp, String signature,
                         byte[] body, Instant now) {
        List<String> errors = new ArrayList<>();

        // Step 1-2: Check required headers
        if (nodeId == null || nodeId.isEmpty()) {
            errors.add("Missing X-Node-Id header");
        }
        if (timestamp == null || timestamp.isEmpty()) {
            errors.add("Missing X-Timestamp header");
        }
        if (signature == null || signature.isEmpty()) {
            errors.add("Missing X-Signature header");
        }
        if (!errors.isEmpty()) {
            return Result.fail(errors);
        }

        // Step 3: Timestamp freshness
        Instant requestTime;
        try {
            requestTime = Instant.parse(timestamp);
        } catch (Exception e) {
            errors.add("Invalid X-Timestamp format: " + timestamp);
            return Result.fail(errors);
        }

        Duration age = Duration.between(requestTime, now).abs();
        if (age.compareTo(MAX_CLOCK_SKEW) > 0) {
            errors.add("Request timestamp too far from server time: " + age.toSeconds() + "s");
            return Result.fail(errors);
        }

        // Step 4-5: Look up peer
        PeerInfo peer;
        try {
            peer = registry.load(nodeId);
        } catch (IOException e) {
            errors.add("Failed to look up peer: " + e.getMessage());
            return Result.fail(errors);
        }

        if (peer == null) {
            errors.add("Unknown peer: " + nodeId);
            return Result.fail(errors);
        }

        // Step 6-7: Verify signature
        PublisherKey peerKey = PublisherKey.fromPublicKey(peer.publicKeyBase64());
        String bodyHash = RequestSigner.hashBytes(body != null ? body : new byte[0]);
        byte[] signingInput = RequestSigner.buildSigningInput(method, path, timestamp, bodyHash);

        boolean sigValid = peerKey.verify(signingInput, signature);
        if (!sigValid) {
            errors.add("Invalid request signature");
            return Result.fail(errors);
        }

        return Result.ok(nodeId);
    }

    /**
     * Verification result.
     *
     * @param valid  true if all checks passed
     * @param nodeId the authenticated node ID (null if invalid)
     * @param errors list of error messages (empty if valid)
     */
    public record Result(boolean valid, String nodeId, List<String> errors) {
        public static Result ok(String nodeId) {
            return new Result(true, nodeId, List.of());
        }

        public static Result fail(List<String> errors) {
            return new Result(false, null, Collections.unmodifiableList(errors));
        }
    }
}
