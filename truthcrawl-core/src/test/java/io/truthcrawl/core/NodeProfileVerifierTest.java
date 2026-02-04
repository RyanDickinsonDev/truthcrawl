package io.truthcrawl.core;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class NodeProfileVerifierTest {

    private static final Instant TIME = Instant.parse("2024-06-01T10:00:00Z");

    @Test
    void valid_registration_only() {
        PublisherKey key = PublisherKey.generate();
        NodeRegistration reg = NodeRegistration.create("Alice", "ACME", "a@b.com", key, TIME);
        NodeProfile profile = new NodeProfile(reg, null);

        NodeProfileVerifier.Result result = NodeProfileVerifier.verify(profile);
        assertTrue(result.valid());
        assertTrue(result.errors().isEmpty());
    }

    @Test
    void valid_with_attestation() {
        PublisherKey key = PublisherKey.generate();
        NodeRegistration reg = NodeRegistration.create("Alice", "ACME", "a@b.com", key, TIME);
        CrawlAttestation att = CrawlAttestation.create(key, List.of("example.com"), TIME);
        NodeProfile profile = new NodeProfile(reg, att);

        NodeProfileVerifier.Result result = NodeProfileVerifier.verify(profile);
        assertTrue(result.valid());
    }

    @Test
    void detects_wrong_node_id() {
        PublisherKey key = PublisherKey.generate();
        NodeRegistration reg = NodeRegistration.create("Alice", "ACME", "a@b.com", key, TIME);

        // Tamper with node_id
        NodeRegistration tampered = new NodeRegistration(
                reg.operatorName(), reg.organization(), reg.contactEmail(),
                "b".repeat(64), reg.publicKey(), reg.registeredAt(), reg.registrationSignature());
        NodeProfile profile = new NodeProfile(tampered, null);

        NodeProfileVerifier.Result result = NodeProfileVerifier.verify(profile);
        assertFalse(result.valid());
        assertTrue(result.errors().stream().anyMatch(e -> e.contains("node_id mismatch")));
    }

    @Test
    void detects_invalid_registration_signature() {
        PublisherKey key = PublisherKey.generate();
        NodeRegistration reg = NodeRegistration.create("Alice", "ACME", "a@b.com", key, TIME);

        // Tamper with signature
        NodeRegistration tampered = new NodeRegistration(
                reg.operatorName(), reg.organization(), reg.contactEmail(),
                reg.nodeId(), reg.publicKey(), reg.registeredAt(), "dGFtcGVyZWQ=");
        NodeProfile profile = new NodeProfile(tampered, null);

        NodeProfileVerifier.Result result = NodeProfileVerifier.verify(profile);
        assertFalse(result.valid());
        assertTrue(result.errors().stream().anyMatch(e -> e.contains("Invalid registration signature")));
    }

    @Test
    void detects_invalid_attestation_signature() {
        PublisherKey key = PublisherKey.generate();
        NodeRegistration reg = NodeRegistration.create("Alice", "ACME", "a@b.com", key, TIME);
        CrawlAttestation att = CrawlAttestation.create(key, List.of("example.com"), TIME);

        // Tamper with attestation signature
        CrawlAttestation tampered = new CrawlAttestation(
                att.nodeId(), att.attestedAt(), att.domains(), "dGFtcGVyZWQ=");
        NodeProfile profile = new NodeProfile(reg, tampered);

        NodeProfileVerifier.Result result = NodeProfileVerifier.verify(profile);
        assertFalse(result.valid());
        assertTrue(result.errors().stream().anyMatch(e -> e.contains("Invalid attestation signature")));
    }

    @Test
    void verification_requires_only_profile() {
        // Create a profile, serialize it, parse it back, and verify
        // This proves verification needs only the profile text
        PublisherKey key = PublisherKey.generate();
        NodeRegistration reg = NodeRegistration.create("Alice", "ACME", "a@b.com", key, TIME);
        CrawlAttestation att = CrawlAttestation.create(key, List.of("example.com"), TIME);
        NodeProfile original = new NodeProfile(reg, att);

        // Serialize and re-parse (simulating receiving the profile text)
        List<String> lines = List.of(original.toCanonicalText().split("\n"));
        NodeProfile parsed = NodeProfile.parse(lines);

        // Verify without the original key
        NodeProfileVerifier.Result result = NodeProfileVerifier.verify(parsed);
        assertTrue(result.valid());
    }

    @Test
    void different_key_fails_verification() {
        PublisherKey key1 = PublisherKey.generate();
        PublisherKey key2 = PublisherKey.generate();

        // Create registration with key1 but embed key2's public key
        NodeRegistration reg = NodeRegistration.create("Alice", "ACME", "a@b.com", key1, TIME);
        NodeRegistration tampered = new NodeRegistration(
                reg.operatorName(), reg.organization(), reg.contactEmail(),
                reg.nodeId(), key2.publicKeyBase64(), reg.registeredAt(), reg.registrationSignature());
        NodeProfile profile = new NodeProfile(tampered, null);

        NodeProfileVerifier.Result result = NodeProfileVerifier.verify(profile);
        assertFalse(result.valid());
    }

    @Test
    void detects_multiple_errors() {
        PublisherKey key = PublisherKey.generate();
        NodeRegistration reg = NodeRegistration.create("Alice", "ACME", "a@b.com", key, TIME);

        // Tamper with both node_id and signature
        NodeRegistration tampered = new NodeRegistration(
                reg.operatorName(), reg.organization(), reg.contactEmail(),
                "c".repeat(64), reg.publicKey(), reg.registeredAt(), "dGFtcGVyZWQ=");
        NodeProfile profile = new NodeProfile(tampered, null);

        NodeProfileVerifier.Result result = NodeProfileVerifier.verify(profile);
        assertFalse(result.valid());
        assertTrue(result.errors().size() >= 2);
    }

    @Test
    void result_ok_is_valid() {
        NodeProfileVerifier.Result ok = NodeProfileVerifier.Result.ok();
        assertTrue(ok.valid());
        assertTrue(ok.errors().isEmpty());
    }

    @Test
    void result_fail_has_errors() {
        NodeProfileVerifier.Result fail = NodeProfileVerifier.Result.fail(List.of("error1", "error2"));
        assertFalse(fail.valid());
        assertEquals(2, fail.errors().size());
    }
}
