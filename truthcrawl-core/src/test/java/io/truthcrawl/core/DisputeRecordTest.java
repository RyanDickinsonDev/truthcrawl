package io.truthcrawl.core;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DisputeRecordTest {

    private static final String CHALLENGED_HASH =
            "b94d27b9934d3e08a52e52d7da7dabfac484efe37a5380ee9088f7ace2efcde9";
    private static final String CHALLENGER_HASH =
            "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";
    private static final Instant FILED_AT = Instant.parse("2024-01-16T10:00:00Z");
    private static final String NODE_ID =
            "0000000000000000000000000000000000000000000000000000000000000000";

    private DisputeRecord base() {
        return new DisputeRecord(
                "2024-01-16-0001", CHALLENGED_HASH, CHALLENGER_HASH,
                "https://example.com", FILED_AT, NODE_ID, null);
    }

    @Test
    void canonical_text_has_fixed_field_order() {
        String text = base().toCanonicalText();
        String[] lines = text.split("\n");
        assertEquals(6, lines.length);
        assertTrue(lines[0].startsWith("dispute_id:"));
        assertTrue(lines[1].startsWith("challenged_record_hash:"));
        assertTrue(lines[2].startsWith("challenger_record_hash:"));
        assertTrue(lines[3].startsWith("url:"));
        assertTrue(lines[4].startsWith("filed_at:"));
        assertTrue(lines[5].startsWith("challenger_node_id:"));
    }

    @Test
    void dispute_hash_is_sha256_of_canonical() {
        String hash = base().disputeHash();
        assertNotNull(hash);
        assertEquals(64, hash.length());
    }

    @Test
    void dispute_hash_is_deterministic() {
        assertEquals(base().disputeHash(), base().disputeHash());
    }

    @Test
    void with_signature_returns_new_record() {
        DisputeRecord unsigned = base();
        assertNull(unsigned.challengerSignature());

        DisputeRecord signed = unsigned.withSignature("sig123");
        assertEquals("sig123", signed.challengerSignature());
        assertNull(unsigned.challengerSignature());
    }

    @Test
    void full_text_includes_signature() {
        DisputeRecord signed = base().withSignature("sig123");
        String text = signed.toFullText();
        assertTrue(text.contains("challenger_signature:sig123"));
    }

    @Test
    void full_text_empty_signature_when_unsigned() {
        String text = base().toFullText();
        assertTrue(text.contains("challenger_signature:\n"));
    }

    @Test
    void parse_round_trip() {
        DisputeRecord original = base().withSignature("sig123");
        List<String> lines = List.of(original.toFullText().split("\n"));
        DisputeRecord parsed = DisputeRecord.parse(lines);

        assertEquals(original.disputeId(), parsed.disputeId());
        assertEquals(original.challengedRecordHash(), parsed.challengedRecordHash());
        assertEquals(original.challengerRecordHash(), parsed.challengerRecordHash());
        assertEquals(original.url(), parsed.url());
        assertEquals(original.filedAt(), parsed.filedAt());
        assertEquals(original.challengerNodeId(), parsed.challengerNodeId());
        assertEquals(original.challengerSignature(), parsed.challengerSignature());
    }

    @Test
    void rejects_empty_dispute_id() {
        assertThrows(IllegalArgumentException.class, () ->
                new DisputeRecord("", CHALLENGED_HASH, CHALLENGER_HASH,
                        "https://example.com", FILED_AT, NODE_ID, null));
    }

    @Test
    void rejects_invalid_challenged_hash_length() {
        assertThrows(IllegalArgumentException.class, () ->
                new DisputeRecord("2024-01-16-0001", "short", CHALLENGER_HASH,
                        "https://example.com", FILED_AT, NODE_ID, null));
    }

    @Test
    void rejects_null_filed_at() {
        assertThrows(IllegalArgumentException.class, () ->
                new DisputeRecord("2024-01-16-0001", CHALLENGED_HASH, CHALLENGER_HASH,
                        "https://example.com", null, NODE_ID, null));
    }
}
