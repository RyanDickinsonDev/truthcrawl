# Testing Strategy

Testing is part of the protocol.

---

## Unit Tests

- Naming: *Test.java
- Scope: pure logic
- No filesystem
- No randomness

---

## Integration Tests

- Naming: *IT.java
- Scope:
  - CLI execution
  - real files
  - real exit codes

Integration tests must never be flaky.

---

## Golden Test Vectors

All canonical and cryptographic behavior must have:
- fixed input
- fixed output
- committed fixtures

If behavior changes, vectors must change explicitly.
