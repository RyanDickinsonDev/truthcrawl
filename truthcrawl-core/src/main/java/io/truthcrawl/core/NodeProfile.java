package io.truthcrawl.core;

import java.util.ArrayList;
import java.util.List;

/**
 * A node profile combining a {@link NodeRegistration} and an optional
 * {@link CrawlAttestation}.
 *
 * <p>File format: registration canonical text, then a blank line, then
 * attestation canonical text. If no attestation exists, the file contains
 * only the registration.
 *
 * <p>The registration and attestation must have the same node_id.
 *
 * @param registration the node's registration (required)
 * @param attestation  the node's crawl attestation (may be null)
 */
public record NodeProfile(
        NodeRegistration registration,
        CrawlAttestation attestation
) {
    public NodeProfile {
        if (registration == null) {
            throw new IllegalArgumentException("registration must not be null");
        }
        if (attestation != null && !attestation.nodeId().equals(registration.nodeId())) {
            throw new IllegalArgumentException(
                    "attestation node_id (" + attestation.nodeId()
                            + ") does not match registration node_id (" + registration.nodeId() + ")");
        }
    }

    /**
     * The node ID from the registration.
     */
    public String nodeId() {
        return registration.nodeId();
    }

    /**
     * Canonical text representation.
     */
    public String toCanonicalText() {
        if (attestation == null) {
            return registration.toCanonicalText();
        }
        return registration.toCanonicalText() + "\n" + attestation.toCanonicalText();
    }

    /**
     * Parse a node profile from canonical text lines.
     *
     * <p>The registration and attestation sections are separated by a blank line.
     */
    public static NodeProfile parse(List<String> lines) {
        // Split on blank line
        List<String> registrationLines = new ArrayList<>();
        List<String> attestationLines = new ArrayList<>();
        boolean inAttestation = false;
        boolean foundBlank = false;

        for (String line : lines) {
            if (!foundBlank && line.strip().isEmpty()) {
                foundBlank = true;
                inAttestation = true;
                continue;
            }
            if (inAttestation) {
                attestationLines.add(line);
            } else {
                registrationLines.add(line);
            }
        }

        if (registrationLines.isEmpty()) {
            throw new IllegalArgumentException("No registration lines found");
        }

        NodeRegistration registration = NodeRegistration.parse(registrationLines);

        CrawlAttestation attestation = null;
        // Filter out empty lines from attestation section
        List<String> filteredAttestation = attestationLines.stream()
                .filter(s -> !s.strip().isEmpty())
                .toList();
        if (!filteredAttestation.isEmpty()) {
            attestation = CrawlAttestation.parse(filteredAttestation);
        }

        return new NodeProfile(registration, attestation);
    }
}
