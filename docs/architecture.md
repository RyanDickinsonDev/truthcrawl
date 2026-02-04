# Architecture Overview

truthcrawl is intentionally minimal.

It is structured as a layered system with strict responsibility boundaries.

---

## Modules

### truthcrawl-core
Responsibilities:
- hashing primitives
- Merkle tree construction
- inclusion proof logic
- proof verification

Rules:
- no filesystem access
- no CLI logic
- no environment dependence

---

### truthcrawl-cli
Responsibilities:
- user-facing commands
- file input/output
- exit codes

All correctness must live in core.

---

### truthcrawl-it
Responsibilities:
- end-to-end verification
- CLI execution
- filesystem interaction

Integration tests prove the system works as a whole.

---

## Explicit Non-Goals

- ranking systems
- crawling logic
- indexing
- dashboards
