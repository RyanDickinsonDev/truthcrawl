# Build Plan (Immediate)

This plan must be followed in order.

---

## Step 1: Maven Scaffolding

- Parent POM (Java 21, JUnit 5)
- Modules:
  - truthcrawl-core
  - truthcrawl-cli
  - truthcrawl-it

At this stage, mvn test must pass with zero code.

---

## Step 2: Core Merkle Tree

Implement:
- SHA-256 hashing
- Merkle tree construction
- Root computation

Add unit tests and golden vectors.

---

## Step 3: CLI Wrappers

Commands:
- build-root
- verify-proof

CLI must delegate all logic to core.

---

## Step 4: Integration Tests

- Run CLI via ProcessBuilder
- Verify output and exit codes

---

## Step 5: CI

- GitHub Actions
- mvn -q verify on every PR
