package io.truthcrawl.core;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class TimestampVerifierTest {

    @Test
    void verifies_valid_token() {
        PublisherKey key = PublisherKey.generate();
        TimestampAuthority tsa = new TimestampAuthority(key);
        TimestampToken token = tsa.issue("a".repeat(64), Instant.parse("2024-01-15T12:00:00Z"));

        TimestampVerifier.Result result = TimestampVerifier.verify(token, key);

        assertTrue(result.valid());
        assertTrue(result.errors().isEmpty());
    }

    @Test
    void rejects_wrong_tsa_key() {
        PublisherKey signingKey = PublisherKey.generate();
        PublisherKey wrongKey = PublisherKey.generate();
        TimestampAuthority tsa = new TimestampAuthority(signingKey);
        TimestampToken token = tsa.issue("a".repeat(64), Instant.parse("2024-01-15T12:00:00Z"));

        TimestampVerifier.Result result = TimestampVerifier.verify(token, wrongKey);

        assertFalse(result.valid());
        assertTrue(result.errors().stream().anyMatch(e -> e.contains("key_id mismatch")));
    }

    @Test
    void rejects_tampered_signature() {
        PublisherKey key = PublisherKey.generate();
        TimestampAuthority tsa = new TimestampAuthority(key);
        TimestampToken token = tsa.issue("a".repeat(64), Instant.parse("2024-01-15T12:00:00Z"));

        // Create token with tampered signature
        TimestampToken tampered = new TimestampToken(
                token.dataHash(), token.issuedAt(), token.tsaKeyId(), "dGFtcGVyZWQ=");

        TimestampVerifier.Result result = TimestampVerifier.verify(tampered, key);

        assertFalse(result.valid());
        assertTrue(result.errors().stream().anyMatch(e -> e.contains("Invalid TSA signature")));
    }

    @Test
    void rejects_tampered_data_hash() {
        PublisherKey key = PublisherKey.generate();
        TimestampAuthority tsa = new TimestampAuthority(key);
        TimestampToken token = tsa.issue("a".repeat(64), Instant.parse("2024-01-15T12:00:00Z"));

        // Create token with different data hash but same signature
        TimestampToken tampered = new TimestampToken(
                "b".repeat(64), token.issuedAt(), token.tsaKeyId(), token.tsaSignature());

        TimestampVerifier.Result result = TimestampVerifier.verify(tampered, key);

        assertFalse(result.valid());
    }

    @Test
    void verification_is_deterministic() {
        PublisherKey key = PublisherKey.generate();
        TimestampAuthority tsa = new TimestampAuthority(key);
        TimestampToken token = tsa.issue("a".repeat(64), Instant.parse("2024-01-15T12:00:00Z"));

        TimestampVerifier.Result r1 = TimestampVerifier.verify(token, key);
        TimestampVerifier.Result r2 = TimestampVerifier.verify(token, key);

        assertEquals(r1.valid(), r2.valid());
    }

    @Test
    void wrong_key_reports_multiple_errors() {
        PublisherKey signingKey = PublisherKey.generate();
        PublisherKey wrongKey = PublisherKey.generate();
        TimestampAuthority tsa = new TimestampAuthority(signingKey);
        TimestampToken token = tsa.issue("a".repeat(64), Instant.parse("2024-01-15T12:00:00Z"));

        TimestampVerifier.Result result = TimestampVerifier.verify(token, wrongKey);

        assertFalse(result.valid());
        // Both key_id mismatch and signature failure
        assertTrue(result.errors().size() >= 1);
    }

    @Test
    void ok_result_has_empty_errors() {
        TimestampVerifier.Result result = TimestampVerifier.Result.ok();
        assertTrue(result.valid());
        assertTrue(result.errors().isEmpty());
    }
}
