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

## Roadmap (high level)

- M0: Spec lock (schemas + log format)
- M1: Transparency log MVP (Merkle root + signed publishing)
- M2: Verification sampling MVP (re-fetch + compare)
- M3: Dispute system (off-chain first)
- M4: Outcome oracles (SERP snapshots / opt-in attestations)

## Contributing

See CONTRIBUTING.md and docs/adr for architecture decisions.
