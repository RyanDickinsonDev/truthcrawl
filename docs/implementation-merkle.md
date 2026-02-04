# Merkle Tree Implementation (v0.1)

This document defines the exact behavior of the Merkle tree.
Any deviation is a breaking change.

---

## Inputs

- Leaves are SHA-256 hashes provided as lowercase hex strings
- Each line in a manifest represents one leaf
- Hex strings must decode to exactly 32 bytes

Invalid input must fail fast.

---

## Hashing Rules

### Leaf Nodes
- Decode hex string to raw 32-byte array
- Do not re-hash leaf values

### Internal Nodes
- Concatenate left_child_bytes || right_child_bytes
- Hash using SHA-256
- Output raw 32-byte hash

---

## Tree Construction

- Build level-by-level
- If a level has an odd number of nodes, duplicate the last node
- Continue until a single node remains

This final node is the Merkle root.

---

## Output Encoding

- Merkle root must be output as lowercase hex
- No prefixes
- No whitespace

---

## Determinism Requirements

Given the same input:
- output must always match bit-for-bit
- behavior must not depend on platform, JVM, or locale
