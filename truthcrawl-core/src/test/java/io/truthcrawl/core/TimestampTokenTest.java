package io.truthcrawl.core;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TimestampTokenTest {

    private static final String HASH = "a".repeat(64);
    private static final String KEY_ID = "b".repeat(64);
    private static final Instant TIME = Instant.parse("2024-01-15T12:00:00Z");
    private static final String SIG = "dGVzdHNpZw=="; // Base64 "testsig"

    @Test
    void canonical_text_format() {
        TimestampToken token = new TimestampToken(HASH, TIME, KEY_ID, SIG);
        String text = token.toCanonicalText();
        assertTrue(text.contains("data_hash:" + HASH));
        assertTrue(text.contains("issued_at:2024-01-15T12:00:00Z"));
        assertTrue(text.contains("tsa_key_id:" + KEY_ID));
        assertTrue(text.contains("tsa_signature:" + SIG));
    }

    @Test
    void parse_roundtrip() {
        TimestampToken original = new TimestampToken(HASH, TIME, KEY_ID, SIG);
        List<String> lines = List.of(original.toCanonicalText().split("\n"));
        TimestampToken parsed = TimestampToken.parse(lines);

        assertEquals(original.dataHash(), parsed.dataHash());
        assertEquals(original.issuedAt(), parsed.issuedAt());
        assertEquals(original.tsaKeyId(), parsed.tsaKeyId());
        assertEquals(original.tsaSignature(), parsed.tsaSignature());
    }

    @Test
    void token_hash_determinism() {
        TimestampToken t1 = new TimestampToken(HASH, TIME, KEY_ID, SIG);
        TimestampToken t2 = new TimestampToken(HASH, TIME, KEY_ID, SIG);
        assertEquals(t1.tokenHash(), t2.tokenHash());
        assertEquals(64, t1.tokenHash().length());
    }

    @Test
    void token_hash_excludes_signature() {
        TimestampToken t1 = new TimestampToken(HASH, TIME, KEY_ID, "c2lnMQ==");
        TimestampToken t2 = new TimestampToken(HASH, TIME, KEY_ID, "c2lnMg==");
        // Same data but different signatures should produce the same tokenHash
        assertEquals(t1.tokenHash(), t2.tokenHash());
    }

    @Test
    void different_data_different_hash() {
        TimestampToken t1 = new TimestampToken(HASH, TIME, KEY_ID, SIG);
        TimestampToken t2 = new TimestampToken("c".repeat(64), TIME, KEY_ID, SIG);
        assertNotEquals(t1.tokenHash(), t2.tokenHash());
    }

    @Test
    void signing_input_is_versioned() {
        TimestampToken token = new TimestampToken(HASH, TIME, KEY_ID, SIG);
        String input = new String(token.signingInput());
        assertTrue(input.startsWith("truthcrawl-timestamp-v1\n"));
    }

    @Test
    void signing_input_does_not_include_signature() {
        TimestampToken token = new TimestampToken(HASH, TIME, KEY_ID, SIG);
        String input = new String(token.signingInput());
        assertFalse(input.contains(SIG));
    }

    @Test
    void signing_input_deterministic() {
        TimestampToken t1 = new TimestampToken(HASH, TIME, KEY_ID, "c2lnMQ==");
        TimestampToken t2 = new TimestampToken(HASH, TIME, KEY_ID, "c2lnMg==");
        assertArrayEquals(t1.signingInput(), t2.signingInput());
    }

    @Test
    void rejects_invalid_data_hash() {
        assertThrows(IllegalArgumentException.class, () ->
                new TimestampToken("short", TIME, KEY_ID, SIG));
    }

    @Test
    void rejects_invalid_key_id() {
        assertThrows(IllegalArgumentException.class, () ->
                new TimestampToken(HASH, TIME, "short", SIG));
    }

    @Test
    void rejects_empty_signature() {
        assertThrows(IllegalArgumentException.class, () ->
                new TimestampToken(HASH, TIME, KEY_ID, ""));
    }

    @Test
    void parse_rejects_wrong_line_count() {
        assertThrows(IllegalArgumentException.class, () ->
                TimestampToken.parse(List.of("data_hash:" + HASH, "issued_at:2024-01-15T12:00:00Z")));
    }
}
