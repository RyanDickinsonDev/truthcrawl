package io.truthcrawl.core;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * A self-signed binding of an operator identity to a node's Ed25519 key pair.
 *
 * <p>Canonical text format:
 * <pre>
 * operator_name:{name}
 * organization:{org}
 * contact_email:{email}
 * node_id:{64-char hex}
 * public_key:{Base64}
 * registered_at:{ISO-8601 UTC}
 * registration_signature:{Base64 Ed25519 signature}
 * </pre>
 *
 * <p>The signing input is a versioned, deterministic byte sequence:
 * <pre>
 * truthcrawl-registration-v1\n
 * operator_name\n
 * organization\n
 * contact_email\n
 * node_id\n
 * registered_at\n
 * </pre>
 *
 * @param operatorName          the operator's name
 * @param organization          the operator's organization
 * @param contactEmail          the operator's contact email
 * @param nodeId                64-char lowercase hex SHA-256 fingerprint of the node's public key
 * @param publicKey             Base64-encoded Ed25519 public key
 * @param registeredAt          when the registration was created (UTC)
 * @param registrationSignature Base64-encoded Ed25519 signature over the signing input
 */
public record NodeRegistration(
        String operatorName,
        String organization,
        String contactEmail,
        String nodeId,
        String publicKey,
        Instant registeredAt,
        String registrationSignature
) {
    private static final String SIGNING_PREFIX = "truthcrawl-registration-v1";

    public NodeRegistration {
        if (operatorName == null || operatorName.isEmpty()) {
            throw new IllegalArgumentException("operator_name must be non-empty");
        }
        if (organization == null || organization.isEmpty()) {
            throw new IllegalArgumentException("organization must be non-empty");
        }
        if (contactEmail == null || contactEmail.isEmpty()) {
            throw new IllegalArgumentException("contact_email must be non-empty");
        }
        if (nodeId == null || nodeId.length() != 64) {
            throw new IllegalArgumentException("node_id must be 64-char hex");
        }
        if (publicKey == null || publicKey.isEmpty()) {
            throw new IllegalArgumentException("public_key must not be empty");
        }
        if (registeredAt == null) {
            throw new IllegalArgumentException("registered_at must not be null");
        }
        if (registrationSignature == null || registrationSignature.isEmpty()) {
            throw new IllegalArgumentException("registration_signature must not be empty");
        }
    }

    /**
     * The exact bytes that the node signs. Versioned to prevent cross-protocol reuse.
     */
    public byte[] signingInput() {
        String message = SIGNING_PREFIX + "\n"
                + operatorName + "\n"
                + organization + "\n"
                + contactEmail + "\n"
                + nodeId + "\n"
                + DateTimeFormatter.ISO_INSTANT.format(registeredAt) + "\n";
        return message.getBytes(StandardCharsets.UTF_8);
    }

    /**
     * Canonical text representation (includes signature).
     */
    public String toCanonicalText() {
        return "operator_name:" + operatorName + "\n"
                + "organization:" + organization + "\n"
                + "contact_email:" + contactEmail + "\n"
                + "node_id:" + nodeId + "\n"
                + "public_key:" + publicKey + "\n"
                + "registered_at:" + DateTimeFormatter.ISO_INSTANT.format(registeredAt) + "\n"
                + "registration_signature:" + registrationSignature + "\n";
    }

    /**
     * Create a signed registration.
     *
     * @param operatorName operator name
     * @param organization organization name
     * @param contactEmail contact email
     * @param nodeKey      the node's Ed25519 key pair (must have private key)
     * @param registeredAt timestamp for the registration
     * @return a signed NodeRegistration
     */
    public static NodeRegistration create(String operatorName,
                                          String organization,
                                          String contactEmail,
                                          PublisherKey nodeKey,
                                          Instant registeredAt) {
        String nodeId = RequestSigner.computeNodeId(nodeKey);
        String pubKeyBase64 = nodeKey.publicKeyBase64();

        // Build signing input to sign
        String message = SIGNING_PREFIX + "\n"
                + operatorName + "\n"
                + organization + "\n"
                + contactEmail + "\n"
                + nodeId + "\n"
                + DateTimeFormatter.ISO_INSTANT.format(registeredAt) + "\n";
        byte[] signingInput = message.getBytes(StandardCharsets.UTF_8);
        String signature = nodeKey.sign(signingInput);

        return new NodeRegistration(operatorName, organization, contactEmail,
                nodeId, pubKeyBase64, registeredAt, signature);
    }

    /**
     * Parse a node registration from canonical text lines.
     */
    public static NodeRegistration parse(List<String> lines) {
        List<String> filtered = lines.stream()
                .map(String::strip)
                .filter(s -> !s.isEmpty())
                .toList();
        if (filtered.size() != 7) {
            throw new IllegalArgumentException(
                    "Expected 7 registration lines, got " + filtered.size());
        }

        String operatorName = parseField(filtered.get(0), "operator_name");
        String organization = parseField(filtered.get(1), "organization");
        String contactEmail = parseField(filtered.get(2), "contact_email");
        String nodeId = parseField(filtered.get(3), "node_id");
        String publicKey = parseField(filtered.get(4), "public_key");
        Instant registeredAt = Instant.parse(parseField(filtered.get(5), "registered_at"));
        String signature = parseField(filtered.get(6), "registration_signature");

        return new NodeRegistration(operatorName, organization, contactEmail,
                nodeId, publicKey, registeredAt, signature);
    }

    private static String parseField(String line, String expectedKey) {
        int colon = line.indexOf(':');
        if (colon == -1) {
            throw new IllegalArgumentException("Missing ':' in line: " + line);
        }
        String key = line.substring(0, colon);
        if (!key.equals(expectedKey)) {
            throw new IllegalArgumentException(
                    "Expected key '" + expectedKey + "', got '" + key + "'");
        }
        return line.substring(colon + 1);
    }
}
