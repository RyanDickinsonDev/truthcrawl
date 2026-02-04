# Tooling

## Language
Java 21 (LTS)

Chosen for stability, safety, and contributor familiarity.

---

## Build System
Maven (multi-module)

Reasons:
- standard in Java OSS
- clean unit vs integration test separation
- predictable CI behavior

---

## IDE Support

Primary:
- VS Code with Java extensions

Implicitly supported:
- Eclipse (via Maven import)

Do not commit IDE metadata:
- .classpath
- .project
- .settings/
- .vscode/
