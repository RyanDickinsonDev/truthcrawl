package io.truthcrawl.core;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class NodeProfileTest {

    private static final Instant TIME = Instant.parse("2024-06-01T10:00:00Z");

    private NodeRegistration makeRegistration(PublisherKey key) {
        return NodeRegistration.create("Alice", "ACME", "alice@acme.com", key, TIME);
    }

    private CrawlAttestation makeAttestation(PublisherKey key) {
        return CrawlAttestation.create(key, List.of("example.com", "test.org"), TIME);
    }

    @Test
    void registration_only_profile() {
        PublisherKey key = PublisherKey.generate();
        NodeRegistration reg = makeRegistration(key);
        NodeProfile profile = new NodeProfile(reg, null);

        assertEquals(reg.nodeId(), profile.nodeId());
        assertNull(profile.attestation());
    }

    @Test
    void registration_and_attestation_profile() {
        PublisherKey key = PublisherKey.generate();
        NodeRegistration reg = makeRegistration(key);
        CrawlAttestation att = makeAttestation(key);
        NodeProfile profile = new NodeProfile(reg, att);

        assertEquals(reg.nodeId(), profile.nodeId());
        assertNotNull(profile.attestation());
        assertEquals(att.domains(), profile.attestation().domains());
    }

    @Test
    void canonical_text_registration_only() {
        PublisherKey key = PublisherKey.generate();
        NodeRegistration reg = makeRegistration(key);
        NodeProfile profile = new NodeProfile(reg, null);

        String text = profile.toCanonicalText();
        assertTrue(text.contains("operator_name:Alice"));
        assertFalse(text.contains("attestation_signature:"));
    }

    @Test
    void canonical_text_with_attestation() {
        PublisherKey key = PublisherKey.generate();
        NodeRegistration reg = makeRegistration(key);
        CrawlAttestation att = makeAttestation(key);
        NodeProfile profile = new NodeProfile(reg, att);

        String text = profile.toCanonicalText();
        assertTrue(text.contains("operator_name:Alice"));
        assertTrue(text.contains("attestation_signature:"));
        assertTrue(text.contains("domain:example.com"));
    }

    @Test
    void parse_roundtrip_registration_only() {
        PublisherKey key = PublisherKey.generate();
        NodeRegistration reg = makeRegistration(key);
        NodeProfile original = new NodeProfile(reg, null);

        List<String> lines = List.of(original.toCanonicalText().split("\n"));
        NodeProfile parsed = NodeProfile.parse(lines);

        assertEquals(original.nodeId(), parsed.nodeId());
        assertEquals(original.registration().operatorName(), parsed.registration().operatorName());
        assertNull(parsed.attestation());
    }

    @Test
    void parse_roundtrip_with_attestation() {
        PublisherKey key = PublisherKey.generate();
        NodeRegistration reg = makeRegistration(key);
        CrawlAttestation att = makeAttestation(key);
        NodeProfile original = new NodeProfile(reg, att);

        List<String> lines = List.of(original.toCanonicalText().split("\n"));
        NodeProfile parsed = NodeProfile.parse(lines);

        assertEquals(original.nodeId(), parsed.nodeId());
        assertNotNull(parsed.attestation());
        assertEquals(original.attestation().domains(), parsed.attestation().domains());
    }

    @Test
    void rejects_null_registration() {
        assertThrows(IllegalArgumentException.class, () ->
                new NodeProfile(null, null));
    }

    @Test
    void rejects_mismatched_node_ids() {
        PublisherKey key1 = PublisherKey.generate();
        PublisherKey key2 = PublisherKey.generate();
        NodeRegistration reg = makeRegistration(key1);
        CrawlAttestation att = makeAttestation(key2);

        assertThrows(IllegalArgumentException.class, () ->
                new NodeProfile(reg, att));
    }

    @Test
    void node_id_delegates_to_registration() {
        PublisherKey key = PublisherKey.generate();
        NodeRegistration reg = makeRegistration(key);
        NodeProfile profile = new NodeProfile(reg, null);
        assertEquals(reg.nodeId(), profile.nodeId());
    }
}
