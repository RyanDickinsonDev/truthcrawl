# truthcrawl

truthcrawl is a verifiable, tamper-evident log of web crawl observations designed to audit SEO claims.

It does not aim to replace search engines.
It aims to make crawl-based claims falsifiable: who observed what, when, and with what confidence.

## What this project is

- A standard observation schema for crawlers/verifiers
- A transparency log format (append-only) for publishing crawl receipts
- Reference implementations for:
  - crawler nodes (produce signed observations)
  - log publisher (build Merkle roots)
  - proof verifier (verify inclusion proofs)
- Analytics primitives for detecting manipulation patterns (optional layer)

## What this project is not

- A promise to improve rankings
- A replacement for Google
- A store of full page content on-chain

## Roadmap

### Complete

- **M0: Spec Lock** -- Observation schema, transparency log format, Merkle tree spec, cryptographic decisions (SHA-256, Ed25519, deterministic serialization)
- **M1: Transparency Log MVP** -- Merkle tree construction/proof verification, batch manifests, signed batch publishing, batch verification
- **M2: Verification Sampling MVP** -- Observation records with node signing, record comparison, deterministic sampling for re-verification
- **M3: Dispute System** -- Dispute records, multi-node observation sets, majority consensus resolution, node reputation tracking
- **M4: Audit Pipeline & Batch Chain** -- Batch chaining with back-references, chain integrity verification, verification sampling, audit reports
- **M5: Record Store & Query** -- Hash-addressed file storage, URL and node indexing, chain-wide statistics
- **M6: Cross-Node Verification Pipeline** -- Batch export/import for offline exchange, automated verification pipeline, verification status tracking

### Planned

- **M7: Trusted Timestamping** -- RFC 3161 timestamp authority integration, timestamp inclusion in observation records, anti-backdating guarantees
- **M8: Content Archival** -- WARC-based page archival alongside content hashes, content retrieval by hash, storage lifecycle policies
- **M9: Network Layer** -- HTTP API for batch discovery and record exchange, peer registration, authenticated endpoints
- **M10: Node Identity & Attestation** -- Operator identity binding, node registration protocol, attestation of crawl capabilities
- **M11: Content Comparison** -- Structural similarity analysis between archived pages, diff generation, plagiarism-relevant field extraction
- **M12: Evidence Packaging** -- Court-ready evidence bundles with chain of custody, human-readable reports with cryptographic backing, third-party audit hooks

## Getting Started

See [USAGE.md](USAGE.md) for installation, Docker setup, CLI commands, and
multi-node configuration.

## Contributing

See CONTRIBUTING.md and docs/adr for architecture decisions.
