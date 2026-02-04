# Merkle Tree Testing Requirements

The Merkle tree is correctness-critical.
Tests are part of the protocol.

---

## Golden Test Vectors

Location:
truthcrawl-core/src/test/resources/vectors/

Required files:
- manifest-3.txt
- expected-root-3.txt

Vectors must be committed and never regenerated silently.

---

## Required Unit Tests

- root_matches_expected_for_manifest
- root_changes_if_leaf_changes
- handles_single_leaf
- duplicates_last_node_on_odd_level
- throws_on_invalid_hex_input

---

## Change Policy

If hashing or construction rules change:
- update this document
- update test vectors explicitly
- document the change in an ADR

Silent behavior changes are forbidden.
