package io.truthcrawl.core;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class NodeRegistrationTest {

    private static final String NODE_ID = "a".repeat(64);
    private static final String PUB_KEY = "MCowBQYDK2VwAyEAtest";
    private static final Instant TIME = Instant.parse("2024-06-01T10:00:00Z");
    private static final String SIG = "dGVzdHNpZw==";

    @Test
    void canonical_text_format() {
        NodeRegistration reg = new NodeRegistration(
                "Alice", "ACME", "alice@acme.com", NODE_ID, PUB_KEY, TIME, SIG);
        String text = reg.toCanonicalText();
        assertTrue(text.contains("operator_name:Alice"));
        assertTrue(text.contains("organization:ACME"));
        assertTrue(text.contains("contact_email:alice@acme.com"));
        assertTrue(text.contains("node_id:" + NODE_ID));
        assertTrue(text.contains("public_key:" + PUB_KEY));
        assertTrue(text.contains("registered_at:2024-06-01T10:00:00Z"));
        assertTrue(text.contains("registration_signature:" + SIG));
    }

    @Test
    void parse_roundtrip() {
        NodeRegistration original = new NodeRegistration(
                "Alice", "ACME", "alice@acme.com", NODE_ID, PUB_KEY, TIME, SIG);
        List<String> lines = List.of(original.toCanonicalText().split("\n"));
        NodeRegistration parsed = NodeRegistration.parse(lines);

        assertEquals(original.operatorName(), parsed.operatorName());
        assertEquals(original.organization(), parsed.organization());
        assertEquals(original.contactEmail(), parsed.contactEmail());
        assertEquals(original.nodeId(), parsed.nodeId());
        assertEquals(original.publicKey(), parsed.publicKey());
        assertEquals(original.registeredAt(), parsed.registeredAt());
        assertEquals(original.registrationSignature(), parsed.registrationSignature());
    }

    @Test
    void signing_input_is_versioned() {
        NodeRegistration reg = new NodeRegistration(
                "Alice", "ACME", "alice@acme.com", NODE_ID, PUB_KEY, TIME, SIG);
        String input = new String(reg.signingInput());
        assertTrue(input.startsWith("truthcrawl-registration-v1\n"));
    }

    @Test
    void signing_input_does_not_include_signature() {
        NodeRegistration reg = new NodeRegistration(
                "Alice", "ACME", "alice@acme.com", NODE_ID, PUB_KEY, TIME, SIG);
        String input = new String(reg.signingInput());
        assertFalse(input.contains(SIG));
    }

    @Test
    void signing_input_does_not_include_public_key() {
        NodeRegistration reg = new NodeRegistration(
                "Alice", "ACME", "alice@acme.com", NODE_ID, PUB_KEY, TIME, SIG);
        String input = new String(reg.signingInput());
        assertFalse(input.contains(PUB_KEY));
    }

    @Test
    void signing_input_deterministic() {
        NodeRegistration r1 = new NodeRegistration(
                "Alice", "ACME", "alice@acme.com", NODE_ID, PUB_KEY, TIME, "c2lnMQ==");
        NodeRegistration r2 = new NodeRegistration(
                "Alice", "ACME", "alice@acme.com", NODE_ID, PUB_KEY, TIME, "c2lnMg==");
        assertArrayEquals(r1.signingInput(), r2.signingInput());
    }

    @Test
    void create_produces_valid_signature() {
        PublisherKey key = PublisherKey.generate();
        NodeRegistration reg = NodeRegistration.create(
                "Alice", "ACME", "alice@acme.com", key, TIME);
        assertTrue(key.verify(reg.signingInput(), reg.registrationSignature()));
    }

    @Test
    void create_sets_correct_node_id() {
        PublisherKey key = PublisherKey.generate();
        NodeRegistration reg = NodeRegistration.create(
                "Alice", "ACME", "alice@acme.com", key, TIME);
        assertEquals(RequestSigner.computeNodeId(key), reg.nodeId());
    }

    @Test
    void create_sets_correct_public_key() {
        PublisherKey key = PublisherKey.generate();
        NodeRegistration reg = NodeRegistration.create(
                "Alice", "ACME", "alice@acme.com", key, TIME);
        assertEquals(key.publicKeyBase64(), reg.publicKey());
    }

    @Test
    void rejects_empty_operator_name() {
        assertThrows(IllegalArgumentException.class, () ->
                new NodeRegistration("", "ACME", "a@b.com", NODE_ID, PUB_KEY, TIME, SIG));
    }

    @Test
    void rejects_empty_organization() {
        assertThrows(IllegalArgumentException.class, () ->
                new NodeRegistration("Alice", "", "a@b.com", NODE_ID, PUB_KEY, TIME, SIG));
    }

    @Test
    void rejects_empty_contact_email() {
        assertThrows(IllegalArgumentException.class, () ->
                new NodeRegistration("Alice", "ACME", "", NODE_ID, PUB_KEY, TIME, SIG));
    }

    @Test
    void rejects_invalid_node_id() {
        assertThrows(IllegalArgumentException.class, () ->
                new NodeRegistration("Alice", "ACME", "a@b.com", "short", PUB_KEY, TIME, SIG));
    }

    @Test
    void rejects_empty_signature() {
        assertThrows(IllegalArgumentException.class, () ->
                new NodeRegistration("Alice", "ACME", "a@b.com", NODE_ID, PUB_KEY, TIME, ""));
    }

    @Test
    void parse_rejects_wrong_line_count() {
        assertThrows(IllegalArgumentException.class, () ->
                NodeRegistration.parse(List.of("operator_name:Alice", "organization:ACME")));
    }
}
