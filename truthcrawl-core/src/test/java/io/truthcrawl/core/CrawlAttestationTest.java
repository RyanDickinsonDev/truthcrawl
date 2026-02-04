package io.truthcrawl.core;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CrawlAttestationTest {

    private static final String NODE_ID = "a".repeat(64);
    private static final Instant TIME = Instant.parse("2024-06-01T10:00:00Z");
    private static final String SIG = "dGVzdHNpZw==";

    @Test
    void canonical_text_format() {
        CrawlAttestation att = new CrawlAttestation(
                NODE_ID, TIME, List.of("example.com", "test.org"), SIG);
        String text = att.toCanonicalText();
        assertTrue(text.contains("node_id:" + NODE_ID));
        assertTrue(text.contains("attested_at:2024-06-01T10:00:00Z"));
        assertTrue(text.contains("domain:example.com"));
        assertTrue(text.contains("domain:test.org"));
        assertTrue(text.contains("attestation_signature:" + SIG));
    }

    @Test
    void domains_are_sorted() {
        CrawlAttestation att = new CrawlAttestation(
                NODE_ID, TIME, List.of("zebra.com", "alpha.com", "middle.com"), SIG);
        assertEquals(List.of("alpha.com", "middle.com", "zebra.com"), att.domains());
    }

    @Test
    void parse_roundtrip() {
        CrawlAttestation original = new CrawlAttestation(
                NODE_ID, TIME, List.of("alpha.com", "beta.org"), SIG);
        List<String> lines = List.of(original.toCanonicalText().split("\n"));
        CrawlAttestation parsed = CrawlAttestation.parse(lines);

        assertEquals(original.nodeId(), parsed.nodeId());
        assertEquals(original.attestedAt(), parsed.attestedAt());
        assertEquals(original.domains(), parsed.domains());
        assertEquals(original.attestationSignature(), parsed.attestationSignature());
    }

    @Test
    void signing_input_is_versioned() {
        CrawlAttestation att = new CrawlAttestation(
                NODE_ID, TIME, List.of("example.com"), SIG);
        String input = new String(att.signingInput());
        assertTrue(input.startsWith("truthcrawl-attestation-v1\n"));
    }

    @Test
    void signing_input_does_not_include_signature() {
        CrawlAttestation att = new CrawlAttestation(
                NODE_ID, TIME, List.of("example.com"), SIG);
        String input = new String(att.signingInput());
        assertFalse(input.contains(SIG));
    }

    @Test
    void signing_input_includes_sorted_domains() {
        CrawlAttestation att = new CrawlAttestation(
                NODE_ID, TIME, List.of("zebra.com", "alpha.com"), SIG);
        String input = new String(att.signingInput());
        int alphaIdx = input.indexOf("alpha.com");
        int zebraIdx = input.indexOf("zebra.com");
        assertTrue(alphaIdx < zebraIdx, "domains should be sorted in signing input");
    }

    @Test
    void signing_input_deterministic() {
        CrawlAttestation a1 = new CrawlAttestation(
                NODE_ID, TIME, List.of("example.com"), "c2lnMQ==");
        CrawlAttestation a2 = new CrawlAttestation(
                NODE_ID, TIME, List.of("example.com"), "c2lnMg==");
        assertArrayEquals(a1.signingInput(), a2.signingInput());
    }

    @Test
    void create_produces_valid_signature() {
        PublisherKey key = PublisherKey.generate();
        CrawlAttestation att = CrawlAttestation.create(
                key, List.of("example.com", "test.org"), TIME);
        assertTrue(key.verify(att.signingInput(), att.attestationSignature()));
    }

    @Test
    void create_normalizes_domains_to_lowercase() {
        PublisherKey key = PublisherKey.generate();
        CrawlAttestation att = CrawlAttestation.create(
                key, List.of("EXAMPLE.COM", "Test.Org"), TIME);
        assertEquals(List.of("example.com", "test.org"), att.domains());
    }

    @Test
    void create_sets_correct_node_id() {
        PublisherKey key = PublisherKey.generate();
        CrawlAttestation att = CrawlAttestation.create(
                key, List.of("example.com"), TIME);
        assertEquals(RequestSigner.computeNodeId(key), att.nodeId());
    }

    @Test
    void rejects_empty_domains() {
        assertThrows(IllegalArgumentException.class, () ->
                new CrawlAttestation(NODE_ID, TIME, List.of(), SIG));
    }

    @Test
    void rejects_invalid_node_id() {
        assertThrows(IllegalArgumentException.class, () ->
                new CrawlAttestation("short", TIME, List.of("example.com"), SIG));
    }

    @Test
    void rejects_empty_signature() {
        assertThrows(IllegalArgumentException.class, () ->
                new CrawlAttestation(NODE_ID, TIME, List.of("example.com"), ""));
    }

    @Test
    void parse_rejects_too_few_lines() {
        assertThrows(IllegalArgumentException.class, () ->
                CrawlAttestation.parse(List.of("node_id:" + NODE_ID, "attested_at:2024-06-01T10:00:00Z")));
    }

    @Test
    void parse_multiple_domains() {
        String text = "node_id:" + NODE_ID + "\n"
                + "attested_at:2024-06-01T10:00:00Z\n"
                + "domain:alpha.com\n"
                + "domain:beta.com\n"
                + "domain:gamma.com\n"
                + "attestation_signature:" + SIG;
        CrawlAttestation parsed = CrawlAttestation.parse(List.of(text.split("\n")));
        assertEquals(3, parsed.domains().size());
        assertEquals(List.of("alpha.com", "beta.com", "gamma.com"), parsed.domains());
    }
}
