package io.truthcrawl.core;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class TimestampAuthorityTest {

    @Test
    void issues_valid_token() {
        PublisherKey key = PublisherKey.generate();
        TimestampAuthority tsa = new TimestampAuthority(key);

        TimestampToken token = tsa.issue("a".repeat(64), Instant.parse("2024-01-15T12:00:00Z"));

        assertEquals("a".repeat(64), token.dataHash());
        assertEquals(Instant.parse("2024-01-15T12:00:00Z"), token.issuedAt());
        assertEquals(tsa.tsaKeyId(), token.tsaKeyId());
        assertNotNull(token.tsaSignature());
    }

    @Test
    void tsa_key_id_is_sha256_of_public_key() {
        PublisherKey key = PublisherKey.generate();
        TimestampAuthority tsa = new TimestampAuthority(key);

        String keyId = tsa.tsaKeyId();
        assertEquals(64, keyId.length());
        assertTrue(keyId.matches("[0-9a-f]{64}"));
    }

    @Test
    void different_keys_different_key_ids() {
        TimestampAuthority tsa1 = new TimestampAuthority(PublisherKey.generate());
        TimestampAuthority tsa2 = new TimestampAuthority(PublisherKey.generate());
        assertNotEquals(tsa1.tsaKeyId(), tsa2.tsaKeyId());
    }

    @Test
    void token_signature_verifiable() {
        PublisherKey key = PublisherKey.generate();
        TimestampAuthority tsa = new TimestampAuthority(key);

        TimestampToken token = tsa.issue("a".repeat(64), Instant.parse("2024-01-15T12:00:00Z"));

        assertTrue(key.verify(token.signingInput(), token.tsaSignature()));
    }

    @Test
    void issue_with_current_time() {
        PublisherKey key = PublisherKey.generate();
        TimestampAuthority tsa = new TimestampAuthority(key);
        Instant before = Instant.now();

        TimestampToken token = tsa.issue("a".repeat(64));

        Instant after = Instant.now();
        assertFalse(token.issuedAt().isBefore(before));
        assertFalse(token.issuedAt().isAfter(after));
    }

    @Test
    void different_hashes_different_signatures() {
        PublisherKey key = PublisherKey.generate();
        TimestampAuthority tsa = new TimestampAuthority(key);
        Instant time = Instant.parse("2024-01-15T12:00:00Z");

        TimestampToken t1 = tsa.issue("a".repeat(64), time);
        TimestampToken t2 = tsa.issue("b".repeat(64), time);

        assertNotEquals(t1.tsaSignature(), t2.tsaSignature());
    }

    @Test
    void same_hash_same_time_same_signature() {
        PublisherKey key = PublisherKey.generate();
        TimestampAuthority tsa = new TimestampAuthority(key);
        Instant time = Instant.parse("2024-01-15T12:00:00Z");

        TimestampToken t1 = tsa.issue("a".repeat(64), time);
        TimestampToken t2 = tsa.issue("a".repeat(64), time);

        // Ed25519 is deterministic, so same input = same signature
        assertEquals(t1.tsaSignature(), t2.tsaSignature());
    }
}
