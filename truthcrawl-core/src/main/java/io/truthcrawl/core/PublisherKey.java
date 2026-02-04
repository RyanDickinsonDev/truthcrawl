package io.truthcrawl.core;

import java.security.InvalidKeyException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.security.SignatureException;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

/**
 * Ed25519 key operations for publisher signing.
 *
 * <p>Uses JDK-native Ed25519 (available since JDK 15).
 *
 * <p>Key encoding:
 * <ul>
 *   <li>Public key: Base64-encoded X.509/SubjectPublicKeyInfo</li>
 *   <li>Private key: Base64-encoded PKCS#8</li>
 *   <li>Signatures: Base64-encoded raw Ed25519 signature</li>
 * </ul>
 */
public final class PublisherKey {

    private static final String ALGORITHM = "Ed25519";

    private final PublicKey publicKey;
    private final PrivateKey privateKey; // null for verify-only instances

    private PublisherKey(PublicKey publicKey, PrivateKey privateKey) {
        this.publicKey = publicKey;
        this.privateKey = privateKey;
    }

    /**
     * Generate a new Ed25519 key pair.
     */
    public static PublisherKey generate() {
        try {
            KeyPairGenerator kpg = KeyPairGenerator.getInstance(ALGORITHM);
            KeyPair kp = kpg.generateKeyPair();
            return new PublisherKey(kp.getPublic(), kp.getPrivate());
        } catch (NoSuchAlgorithmException e) {
            throw new AssertionError("Ed25519 must be available in JDK 21", e);
        }
    }

    /**
     * Load a verify-only instance from a Base64-encoded public key.
     */
    public static PublisherKey fromPublicKey(String base64PublicKey) {
        try {
            byte[] decoded = Base64.getDecoder().decode(base64PublicKey);
            KeyFactory kf = KeyFactory.getInstance(ALGORITHM);
            PublicKey pub = kf.generatePublic(new X509EncodedKeySpec(decoded));
            return new PublisherKey(pub, null);
        } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
            throw new IllegalArgumentException("Invalid Ed25519 public key", e);
        }
    }

    /**
     * Load a signing instance from Base64-encoded public and private keys.
     */
    public static PublisherKey fromKeyPair(String base64PublicKey, String base64PrivateKey) {
        try {
            byte[] pubBytes = Base64.getDecoder().decode(base64PublicKey);
            byte[] privBytes = Base64.getDecoder().decode(base64PrivateKey);
            KeyFactory kf = KeyFactory.getInstance(ALGORITHM);
            PublicKey pub = kf.generatePublic(new X509EncodedKeySpec(pubBytes));
            PrivateKey priv = kf.generatePrivate(new PKCS8EncodedKeySpec(privBytes));
            return new PublisherKey(pub, priv);
        } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
            throw new IllegalArgumentException("Invalid Ed25519 key pair", e);
        }
    }

    /**
     * Sign the given message bytes.
     *
     * @param message bytes to sign
     * @return Base64-encoded Ed25519 signature
     * @throws IllegalStateException if this is a verify-only instance
     */
    public String sign(byte[] message) {
        if (privateKey == null) {
            throw new IllegalStateException("No private key available for signing");
        }
        try {
            Signature sig = Signature.getInstance(ALGORITHM);
            sig.initSign(privateKey);
            sig.update(message);
            return Base64.getEncoder().encodeToString(sig.sign());
        } catch (NoSuchAlgorithmException | InvalidKeyException | SignatureException e) {
            throw new AssertionError("Ed25519 signing failed", e);
        }
    }

    /**
     * Verify a signature over the given message.
     *
     * @param message         bytes that were signed
     * @param base64Signature Base64-encoded Ed25519 signature
     * @return true if the signature is valid
     */
    public boolean verify(byte[] message, String base64Signature) {
        try {
            Signature sig = Signature.getInstance(ALGORITHM);
            sig.initVerify(publicKey);
            sig.update(message);
            return sig.verify(Base64.getDecoder().decode(base64Signature));
        } catch (NoSuchAlgorithmException | InvalidKeyException | SignatureException e) {
            return false;
        }
    }

    /**
     * Base64-encoded public key (X.509/SubjectPublicKeyInfo format).
     */
    public String publicKeyBase64() {
        return Base64.getEncoder().encodeToString(publicKey.getEncoded());
    }

    /**
     * Base64-encoded private key (PKCS#8 format).
     *
     * @throws IllegalStateException if this is a verify-only instance
     */
    public String privateKeyBase64() {
        if (privateKey == null) {
            throw new IllegalStateException("No private key available");
        }
        return Base64.getEncoder().encodeToString(privateKey.getEncoded());
    }
}
