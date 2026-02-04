# ObservationRecord v0.1 (draft)

An ObservationRecord is produced by a crawler/verifier node and signed.

Core fields (required):
- version: string (e.g. "0.1")
- observed_at: ISO-8601 UTC timestamp
- url: original URL string as requested
- final_url: URL after redirects
- status_code: integer
- fetch_ms: integer
- content_hash: sha256 of normalized HTML/DOM snapshot
- headers_subset: object (selected headers only)
- directives:
  - canonical: string|null
  - robots_meta: string|null
  - robots_header: string|null
- outbound_links: array of normalized URLs (sorted)
- node:
  - node_id: string (public key fingerprint)
  - signature: base64 (signature over canonical serialization of record without signature)

Normalization rules:
- URL normalization must be deterministic and documented
- outbound_links must be de-duplicated and sorted
- content_hash must be computed over a well-defined normalization pipeline
