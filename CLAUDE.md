## Current Objective (Lock This In)

The Merkle tree core, M1 (Transparency Log MVP), M2 (Verification Sampling MVP),
M3 (Dispute System), M4 (Audit Pipeline & Batch Chain), and M5 (Record Store & Query)
milestones are complete.

The next implementation milestone is **M6: Cross-Node Verification Pipeline**.

This milestone enables multi-party verification without requiring a network layer.
Batch packages can be exported, exchanged via any transport, imported into a local
store, and verified through an automated pipeline that samples, compares, and reports.

Nothing outside this scope is allowed to proceed until M6 passes.

---

## M6 Scope (Strict)

M6 is complete when the system supports:

- batch export: package a published batch (metadata, manifest, chain link, signature, records) into a self-contained directory
- batch import: unpack a batch package into the local record store, validating integrity on import
- verification pipeline: orchestrate sample → compare → report → dispute for an imported batch
- verification status: track which batches/records have been verified and their outcomes

Out of scope for M6:
- network protocols or HTTP APIs
- peer discovery or DHT
- real-time streaming or push notifications
- distributed consensus beyond existing dispute resolution
- UI dashboards

---

## Implementation Order (M6)

1. Implement BatchExporter (packages a published batch into an exportable directory)
2. Implement BatchImporter (imports a batch package, validates, stores records)
3. Implement VerificationPipeline (orchestrates: sample → re-observe → compare → report → dispute)
4. Implement VerificationStatus (tracks per-batch and per-record verification outcomes)
5. CLI commands: export-batch, import-batch, verify-pipeline, verification-status
6. Unit tests for export, import, pipeline, and status tracking
7. Integration tests for end-to-end cross-node verification

Skipping steps is not allowed.

---

## Batch Export Rules (Locked)

- An exported batch is a self-contained directory with a fixed layout:
  ```
  batch-{batchId}/
    metadata.txt        (BatchMetadata canonical text)
    manifest.txt        (BatchManifest canonical text)
    chain-link.txt      (ChainLink canonical text)
    signature.txt       (Base64 publisher signature)
    records/
      {hash}.txt        (ObservationRecord full text, one per manifest entry)
  ```
- All manifest entries must have their records present in the export
- The directory is self-verifiable: given the publisher's public key, the entire batch
  can be verified from the export alone (no external data needed)
- Exporting a batch is deterministic: same inputs always produce byte-identical files

---

## Batch Import Rules (Locked)

- Import reads an export directory, validates integrity, and stores records
- Validation on import: signature valid, manifest_hash matches, merkle_root matches,
  record_count matches, all records present and parseable, record hashes match manifest
- Records are stored in the local RecordStore (idempotent — re-importing is safe)
- Import produces a receipt: batch_id, records_imported, records_already_present, valid (boolean)
- Import never modifies existing records in the store (append-only invariant preserved)

---

## Verification Pipeline Rules (Locked)

- The pipeline operates on an imported batch using local independent observations
- Steps (in order):
  1. Sample records from the batch manifest (using VerificationSampler)
  2. For each sampled record, look up independent observations of the same URL
     from the local record store (different node_id required)
  3. Compare sampled records against independent observations (using RecordComparator)
  4. Generate an AuditReport summarizing matches/mismatches
  5. For each mismatch, optionally file a dispute (using DisputeRecord)
- The pipeline requires a minimum number of independent observations per URL
  (configurable, default 1) to proceed with comparison
- Records with no independent observations are marked as "unverifiable" in the report
- Pipeline output: AuditReport + list of filed disputes + per-record verification detail

---

## Verification Status Rules (Locked)

- Tracks the outcome of verification for each batch and each record
- Batch status: PENDING, VERIFIED_CLEAN, VERIFIED_WITH_DISPUTES, UNVERIFIABLE
- Record status: NOT_CHECKED, MATCHED, MISMATCHED, UNVERIFIABLE (no independent data)
- Status is stored as canonical text in a file: `verification/{batch-id}.txt`
- Status layout:
  ```
  batch_id:{id}
  batch_status:{status}
  records_total:{n}
  records_checked:{n}
  records_matched:{n}
  records_mismatched:{n}
  records_unverifiable:{n}
  checked_at:{ISO-8601}
  ```
- Status is deterministic: same verification run on same data produces same status
- Status files are append-only within the verification directory

---

## Cryptographic Decisions (Unchanged)

- Hashing: SHA-256
- Merkle tree: existing implementation
- Signatures: Ed25519 (JDK)
- Encoding: UTF-8
- Serialization: explicit text format, deterministic field order
- node_id: SHA-256 fingerprint of the node's Ed25519 public key (lowercase hex)

---

## Build Stop Conditions (Extended)

Work must stop immediately if:
- exported batch is not self-verifiable from the export directory alone
- import modifies or overwrites existing records in the store
- pipeline produces different results for the same batch and same local data
- verification status differs for the same verification run
- re-importing the same batch changes any stored data

Every claim, including verification status and pipeline results, must be independently
verifiable by an untrusted third party.
