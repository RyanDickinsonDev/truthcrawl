package io.truthcrawl.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Verifies {@link TimestampToken}s against a TSA public key.
 *
 * <p>Verification checks:
 * <ol>
 *   <li>tsa_key_id matches SHA-256 fingerprint of the provided TSA public key</li>
 *   <li>tsa_signature is valid over the signing input</li>
 * </ol>
 *
 * <p>Verification requires only the token and the TSA public key.
 */
public final class TimestampVerifier {

    private TimestampVerifier() {}

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
     * Verify a timestamp token against the given TSA public key.
     *
     * @param token  the token to verify
     * @param tsaKey the TSA's public key
     * @return verification result
     */
    public static Result verify(TimestampToken token, PublisherKey tsaKey) {
        List<String> errors = new ArrayList<>();

        // Check tsa_key_id matches the provided key
        String expectedKeyId = TimestampAuthority.computeKeyId(tsaKey);
        if (!token.tsaKeyId().equals(expectedKeyId)) {
            errors.add("tsa_key_id mismatch: token has " + token.tsaKeyId()
                    + " but provided key computes " + expectedKeyId);
        }

        // Check signature
        boolean sigValid = tsaKey.verify(token.signingInput(), token.tsaSignature());
        if (!sigValid) {
            errors.add("Invalid TSA signature");
        }

        if (errors.isEmpty()) {
            return Result.ok();
        }
        return Result.fail(errors);
    }
}
