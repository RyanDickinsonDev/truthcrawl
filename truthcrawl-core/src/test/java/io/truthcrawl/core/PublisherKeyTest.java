package io.truthcrawl.core;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PublisherKeyTest {

    private static final byte[] MESSAGE = "test message".getBytes(StandardCharsets.UTF_8);

    @Test
    void sign_and_verify_round_trip() {
        PublisherKey key = PublisherKey.generate();
        String sig = key.sign(MESSAGE);
        assertTrue(key.verify(MESSAGE, sig));
    }

    @Test
    void verify_rejects_wrong_message() {
        PublisherKey key = PublisherKey.generate();
        String sig = key.sign(MESSAGE);
        assertFalse(key.verify("wrong message".getBytes(StandardCharsets.UTF_8), sig));
    }

    @Test
    void verify_rejects_wrong_key() {
        PublisherKey key1 = PublisherKey.generate();
        PublisherKey key2 = PublisherKey.generate();
        String sig = key1.sign(MESSAGE);
        assertFalse(key2.verify(MESSAGE, sig));
    }

    @Test
    void key_serialization_round_trip() {
        PublisherKey original = PublisherKey.generate();
        String sig = original.sign(MESSAGE);

        // Reconstruct from serialized keys
        PublisherKey restored = PublisherKey.fromKeyPair(
                original.publicKeyBase64(), original.privateKeyBase64());

        // Restored key verifies the original signature
        assertTrue(restored.verify(MESSAGE, sig));

        // Restored key produces valid new signatures
        String sig2 = restored.sign(MESSAGE);
        assertTrue(original.verify(MESSAGE, sig2));
    }

    @Test
    void public_key_only_can_verify() {
        PublisherKey full = PublisherKey.generate();
        String sig = full.sign(MESSAGE);

        PublisherKey pubOnly = PublisherKey.fromPublicKey(full.publicKeyBase64());
        assertTrue(pubOnly.verify(MESSAGE, sig));
    }

    @Test
    void public_key_only_cannot_sign() {
        PublisherKey full = PublisherKey.generate();
        PublisherKey pubOnly = PublisherKey.fromPublicKey(full.publicKeyBase64());
        assertThrows(IllegalStateException.class, () -> pubOnly.sign(MESSAGE));
    }

    @Test
    void generated_keys_are_not_null() {
        PublisherKey key = PublisherKey.generate();
        assertNotNull(key.publicKeyBase64());
        assertNotNull(key.privateKeyBase64());
    }
}
