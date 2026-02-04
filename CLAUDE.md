## Current Objective (Lock This In)

The Merkle tree core, M1 (Transparency Log MVP), M2 (Verification Sampling MVP),
M3 (Dispute System), M4 (Audit Pipeline & Batch Chain), M5 (Record Store & Query),
M6 (Cross-Node Verification Pipeline), M7 (Trusted Timestamping),
M8 (Content Archival), and M9 (Network Layer) milestones are complete.

The next implementation milestone is **M10: Node Identity & Attestation**.

This milestone binds cryptographic node identities to real-world operators and
lets nodes declare their crawl capabilities. A node registration is a self-signed
document that binds an operator name, organization, and contact email to a node's
Ed25519 key pair. A crawl attestation is a signed declaration of which domains
the node crawls. Both are independently verifiable using only the node's public key.

Nothing outside this scope is allowed to proceed until M10 passes.

---

## M10 Scope (Strict)

M10 is complete when the system supports:

- operator identity: an immutable record of a node operator (name, organization, contact email)
- node registration: a self-signed binding of operator identity to a node's key pair
- crawl attestation: a signed declaration of domains the node crawls
- node profile: the combination of registration + attestation for a node
- node profile store: persistent storage and lookup of profiles by node ID
- node profile verification: independent verification of registration and attestation signatures

Out of scope for M10:
- external identity providers (OAuth, OIDC, X.509 certificates)
- identity revocation or expiry
- capability enforcement (attestations are claims, not permissions)
- reputation scoring based on identity (M3 already handles reputation)
- modifying existing ObservationRecord, ChainLink, or PeerInfo classes

---

## Implementation Order (M10)

1. Implement NodeRegistration (self-signed binding: operator identity + node key + timestamp)
2. Implement CrawlAttestation (signed declaration: node ID + list of domains + timestamp)
3. Implement NodeProfile (registration + attestation combined)
4. Implement NodeProfileStore (persistent file-based profile store)
5. Implement NodeProfileVerifier (verifies registration and attestation signatures)
6. CLI commands: register-node, attest-capabilities, node-profile, verify-node
7. Unit tests for registration, attestation, profile, store, and verifier
8. Integration tests for end-to-end identity workflow

Skipping steps is not allowed.

---

## NodeRegistration Rules (Locked)

- A node registration binds an operator identity to a node's Ed25519 key pair
- The registration is self-signed: the node signs its own registration
- Canonical text format:
  ```
  operator_name:{name}
  organization:{org}
  contact_email:{email}
  node_id:{64-char hex}
  public_key:{Base64}
  registered_at:{ISO-8601 UTC}
  registration_signature:{Base64 Ed25519 signature}
  ```
- The signing input is a versioned, deterministic byte sequence:
  ```
  truthcrawl-registration-v1\n
  operator_name\n
  organization\n
  contact_email\n
  node_id\n
  registered_at\n
  ```
- The node signs the signing input (NOT the canonical text, which includes the signature)
- operator_name and organization must be non-empty
- contact_email must be non-empty
- Registrations are immutable once signed

---

## CrawlAttestation Rules (Locked)

- A crawl attestation is a signed declaration of which domains a node crawls
- The attestation is self-signed by the same node key as the registration
- Canonical text format:
  ```
  node_id:{64-char hex}
  attested_at:{ISO-8601 UTC}
  domain:{domain1}
  domain:{domain2}
  ...
  attestation_signature:{Base64 Ed25519 signature}
  ```
- The signing input is a versioned, deterministic byte sequence:
  ```
  truthcrawl-attestation-v1\n
  node_id\n
  attested_at\n
  domain1\n
  domain2\n
  ...
  ```
- Domains are sorted alphabetically in the canonical text and signing input
- At least one domain is required
- Domains are lowercase, no protocol prefix (e.g. "example.com" not "https://example.com")
- Attestations are immutable once signed

---

## NodeProfile Rules (Locked)

- A node profile combines a registration and an optional crawl attestation
- The profile is stored as a single file: `profiles/{node-id}.txt`
- File format: registration canonical text, then a blank line, then attestation canonical text
- If no attestation exists, the file contains only the registration
- The registration and attestation must have the same node_id
- Profiles can be looked up by node ID (exact match)
- Listing profiles returns a sorted list of node IDs

---

## NodeProfileVerifier Rules (Locked)

- Verification checks for registration:
  1. Registration is parseable and has all required fields
  2. node_id matches SHA-256 fingerprint of the provided public key
  3. registration_signature is valid over the signing input
- Verification checks for attestation (if present):
  1. Attestation is parseable and has all required fields
  2. node_id matches the registration's node_id
  3. attestation_signature is valid over the signing input using the same public key
- Verification requires only the profile text (the public key is embedded in the registration)
- Verification is deterministic: same profile always produces the same result

---

## Cryptographic Decisions (Unchanged)

- Hashing: SHA-256
- Merkle tree: existing implementation
- Signatures: Ed25519 (JDK)
- Encoding: UTF-8
- Serialization: explicit text format, deterministic field order
- node_id: SHA-256 fingerprint of the node's Ed25519 public key (lowercase hex)
- tsa_key_id: SHA-256 fingerprint of the TSA's Ed25519 public key (lowercase hex)
- content hash: SHA-256 of raw payload bytes (lowercase hex)
- request body hash: SHA-256 of raw request body bytes (lowercase hex)

---

## Build Stop Conditions (Extended)

Work must stop immediately if:
- a node registration can be created without a valid Ed25519 signature
- a registration can be verified against a different key than the one embedded in it
- node_id in a registration does not match the SHA-256 fingerprint of the embedded public key
- a crawl attestation can be signed by a different key than the registration
- the signing input includes the signature itself (circular dependency)
- verification requires anything beyond the profile text itself
- an attestation's node_id differs from its registration's node_id

Every identity claim must be independently verifiable by an untrusted third party
using only the profile document itself. The public key is embedded in the
registration, and all signatures are verifiable against it.
