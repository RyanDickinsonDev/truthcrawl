package io.truthcrawl.core;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Deterministic selection of records from a manifest for re-verification.
 *
 * <p>Sampling rules:
 * <ul>
 *   <li>Seed = SHA-256(batch_merkle_root + user_provided_seed)</li>
 *   <li>Selection: convert seed bytes to indices via modular arithmetic, no replacement</li>
 *   <li>Default sample size = min(10, manifest_size)</li>
 *   <li>Output is a sorted list of record hashes</li>
 *   <li>Deterministic: same seed + same manifest always produces the same selection</li>
 * </ul>
 */
public final class VerificationSampler {

    /** Default maximum sample size. */
    public static final int DEFAULT_SAMPLE_SIZE = 10;

    private VerificationSampler() {}

    /**
     * Select records for re-verification using default sample size.
     *
     * @param manifest   the batch manifest
     * @param merkleRoot the batch's Merkle root (used in seed derivation)
     * @param userSeed   user-provided seed string (ensures different auditors get different samples)
     * @return sorted list of selected record hashes
     */
    public static List<String> sample(BatchManifest manifest, String merkleRoot, String userSeed) {
        return sample(manifest, merkleRoot, userSeed, DEFAULT_SAMPLE_SIZE);
    }

    /**
     * Select records for re-verification.
     *
     * @param manifest      the batch manifest
     * @param merkleRoot    the batch's Merkle root (used in seed derivation)
     * @param userSeed      user-provided seed string
     * @param maxSampleSize maximum number of records to select
     * @return sorted list of selected record hashes
     */
    public static List<String> sample(BatchManifest manifest, String merkleRoot,
                                       String userSeed, int maxSampleSize) {
        if (maxSampleSize < 1) {
            throw new IllegalArgumentException("maxSampleSize must be >= 1");
        }

        List<String> entries = manifest.entries();
        int n = entries.size();
        int sampleSize = Math.min(maxSampleSize, n);

        // Derive seed: SHA-256(merkleRoot + userSeed)
        byte[] seedBytes = deriveSeed(merkleRoot, userSeed);

        // Select indices without replacement
        Set<Integer> selectedIndices = new LinkedHashSet<>();
        int round = 0;
        while (selectedIndices.size() < sampleSize) {
            byte[] roundSeed = deriveRoundSeed(seedBytes, round);
            int index = bytesToIndex(roundSeed, n);
            selectedIndices.add(index);
            round++;
        }

        // Collect selected hashes and sort
        List<String> selected = new ArrayList<>();
        for (int idx : selectedIndices) {
            selected.add(entries.get(idx));
        }
        Collections.sort(selected);
        return Collections.unmodifiableList(selected);
    }

    /**
     * Derive the base seed from merkle root and user seed.
     */
    static byte[] deriveSeed(String merkleRoot, String userSeed) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(merkleRoot.getBytes(StandardCharsets.UTF_8));
            digest.update(userSeed.getBytes(StandardCharsets.UTF_8));
            return digest.digest();
        } catch (NoSuchAlgorithmException e) {
            throw new AssertionError("SHA-256 must be available", e);
        }
    }

    /**
     * Derive a per-round seed to handle collisions (no-replacement selection).
     */
    private static byte[] deriveRoundSeed(byte[] baseSeed, int round) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(baseSeed);
            digest.update(intToBytes(round));
            return digest.digest();
        } catch (NoSuchAlgorithmException e) {
            throw new AssertionError("SHA-256 must be available", e);
        }
    }

    /**
     * Convert seed bytes to an index in [0, n) using modular arithmetic.
     * Uses the first 8 bytes as an unsigned long to minimize bias for reasonable n values.
     */
    private static int bytesToIndex(byte[] seed, int n) {
        long value = 0;
        for (int i = 0; i < 8; i++) {
            value = (value << 8) | (seed[i] & 0xFFL);
        }
        // Ensure positive by masking sign bit
        value = value & 0x7FFF_FFFF_FFFF_FFFFL;
        return (int) (value % n);
    }

    private static byte[] intToBytes(int value) {
        return new byte[]{
                (byte) (value >> 24),
                (byte) (value >> 16),
                (byte) (value >> 8),
                (byte) value
        };
    }
}
