package io.truthcrawl.core;

/**
 * One step of a Merkle inclusion proof.
 *
 * @param siblingHash raw 32-byte sibling hash
 * @param position    which side the sibling is on
 */
public record ProofStep(byte[] siblingHash, Position position) {

    public enum Position {
        /** Sibling is on the left: hash(sibling || current). */
        LEFT,
        /** Sibling is on the right: hash(current || sibling). */
        RIGHT
    }
}
