# Transparency Log v0.1 (draft)

The transparency log is append-only.

Daily batch:
- input: N ObservationRecord hashes
- output:
  - merkle_root: sha256
  - batch_id: YYYY-MM-DD
  - batch_manifest: list of record hashes (or reference to stored manifest)
  - publisher_signature: signature over (batch_id + merkle_root + manifest_hash)

Publishing:
- commit batch manifest (or a pointer + hash)
- create signed tag "log/YYYY-MM-DD"
- optionally create a GitHub Release including:
  - merkle_root
  - manifest_hash
  - dataset pointer

Verification:
- given (record, inclusion_proof, merkle_root) => verify inclusion
