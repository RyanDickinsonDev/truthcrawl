package io.truthcrawl.core;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;

/**
 * Deterministic Merkle tree over SHA-256 leaf hashes.
 *
 * <p>Spec: docs/implementation-merkle.md
 *
 * <ul>
 *   <li>Leaves are raw 32-byte arrays decoded from lowercase hex.</li>
 *   <li>Leaves are NOT re-hashed.</li>
 *   <li>Internal nodes = SHA-256(left || right).</li>
 *   <li>Odd levels duplicate the last node.</li>
 *   <li>Root is returned as lowercase hex, no prefix, no whitespace.</li>
 * </ul>
 */
public final class MerkleTree {

    private MerkleTree() {}

    /**
     * Compute the Merkle root for the given leaf hashes.
     *
     * @param leafHexStrings non-empty list of lowercase hex SHA-256 hashes (64 chars each)
     * @return Merkle root as lowercase hex
     * @throws IllegalArgumentException if input is empty or contains invalid hex
     */
    public static String computeRoot(List<String> leafHexStrings) {
        if (leafHexStrings.isEmpty()) {
            throw new IllegalArgumentException("Leaf list must not be empty");
        }

        List<byte[]> level = new ArrayList<>(leafHexStrings.size());
        for (String hex : leafHexStrings) {
            level.add(decodeHex(hex));
        }

        while (level.size() > 1) {
            if (level.size() % 2 != 0) {
                level.add(level.get(level.size() - 1));
            }
            List<byte[]> next = new ArrayList<>(level.size() / 2);
            for (int i = 0; i < level.size(); i += 2) {
                next.add(hashPair(level.get(i), level.get(i + 1)));
            }
            level = next;
        }

        return encodeHex(level.get(0));
    }

    /**
     * Generate an inclusion proof for the leaf at the given index.
     *
     * @param leafHexStrings non-empty list of lowercase hex SHA-256 hashes
     * @param leafIndex      index of the leaf to prove (0-based)
     * @return ordered list of proof steps from leaf to root
     * @throws IllegalArgumentException if input is empty, contains invalid hex, or index is out of range
     */
    public static List<ProofStep> computeProof(List<String> leafHexStrings, int leafIndex) {
        if (leafHexStrings.isEmpty()) {
            throw new IllegalArgumentException("Leaf list must not be empty");
        }
        if (leafIndex < 0 || leafIndex >= leafHexStrings.size()) {
            throw new IllegalArgumentException(
                    "Leaf index " + leafIndex + " out of range [0, " + leafHexStrings.size() + ")");
        }

        List<byte[]> level = new ArrayList<>(leafHexStrings.size());
        for (String hex : leafHexStrings) {
            level.add(decodeHex(hex));
        }

        List<ProofStep> proof = new ArrayList<>();
        int idx = leafIndex;

        while (level.size() > 1) {
            if (level.size() % 2 != 0) {
                level.add(level.get(level.size() - 1));
            }

            int siblingIdx = (idx % 2 == 0) ? idx + 1 : idx - 1;
            ProofStep.Position pos = (idx % 2 == 0)
                    ? ProofStep.Position.RIGHT
                    : ProofStep.Position.LEFT;
            proof.add(new ProofStep(level.get(siblingIdx), pos));

            List<byte[]> next = new ArrayList<>(level.size() / 2);
            for (int i = 0; i < level.size(); i += 2) {
                next.add(hashPair(level.get(i), level.get(i + 1)));
            }
            level = next;
            idx = idx / 2;
        }

        return proof;
    }

    /**
     * Verify that a leaf hash is included in a Merkle root using the given proof.
     *
     * @param leafHex      lowercase hex leaf hash (64 chars)
     * @param proof        ordered proof steps from leaf to root
     * @param expectedRoot lowercase hex expected Merkle root
     * @return true if the proof is valid
     * @throws IllegalArgumentException if hex inputs are invalid
     */
    public static boolean verifyProof(String leafHex, List<ProofStep> proof, String expectedRoot) {
        byte[] current = decodeHex(leafHex);

        for (ProofStep step : proof) {
            if (step.position() == ProofStep.Position.LEFT) {
                current = hashPair(step.siblingHash(), current);
            } else {
                current = hashPair(current, step.siblingHash());
            }
        }

        return encodeHex(current).equals(expectedRoot);
    }

    /**
     * Encode a raw 32-byte hash as lowercase hex.
     */
    public static String encodeHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(Character.forDigit((b >> 4) & 0xf, 16));
            sb.append(Character.forDigit(b & 0xf, 16));
        }
        return sb.toString();
    }

    /**
     * Decode a lowercase hex string to raw bytes.
     */
    public static byte[] decodeHex(String hex) {
        if (hex.length() != 64) {
            throw new IllegalArgumentException(
                    "Expected 64-character hex string, got length " + hex.length());
        }
        byte[] bytes = new byte[32];
        for (int i = 0; i < 32; i++) {
            int hi = Character.digit(hex.charAt(i * 2), 16);
            int lo = Character.digit(hex.charAt(i * 2 + 1), 16);
            if (hi == -1 || lo == -1) {
                throw new IllegalArgumentException(
                        "Invalid hex character at position " + (i * 2));
            }
            bytes[i] = (byte) ((hi << 4) | lo);
        }
        return bytes;
    }

    private static byte[] hashPair(byte[] left, byte[] right) {
        MessageDigest digest = sha256();
        digest.update(left);
        digest.update(right);
        return digest.digest();
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new AssertionError("SHA-256 must be available", e);
        }
    }
}
