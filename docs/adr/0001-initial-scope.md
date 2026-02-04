# ADR 0001: Initial Scope = transparency log before blockchain

Status: accepted

Decision:
Start with a transparency log (Merkle roots + signed publishing) rather than a blockchain.

Rationale:
- faster iteration
- fewer moving parts
- keeps focus on falsifiability and provenance
- preserves ability to migrate to on-chain settlement later if incentives require it

Consequences:
- no token economics in MVP
- verification/disputes start as off-chain processes
