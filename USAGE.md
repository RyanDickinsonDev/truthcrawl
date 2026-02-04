# Usage Guide

## Quick Start (Docker)

The fastest way to run truthcrawl is with Docker. A single command starts an
autonomous node that crawls, batches, timestamps, and syncs automatically.

```sh
docker run -d \
  -p 8080:8080 \
  -v truthcrawl-data:/data \
  rydickin/truthcrawl
```

Open `http://localhost:8080` in your browser to see the node dashboard.

Keys are generated automatically on first run. Data persists in the
`truthcrawl-data` Docker volume.

### Adding URLs to crawl

The node reads URLs from a file called `urls.txt` inside the data directory.
With a Docker volume:

```sh
docker exec -it <container-id> sh -c 'echo "https://example.com" >> /data/urls.txt'
```

Or mount a local directory instead of a named volume:

```sh
docker run -d \
  -p 8080:8080 \
  -v ~/truthcrawl-data:/data \
  rydickin/truthcrawl
```

Then edit `~/truthcrawl-data/urls.txt` directly. One URL per line, `#` for
comments:

```
# Sites to crawl
https://example.com
https://affordabledoorsandmore.com
```

The node crawls all listed URLs once per hour by default.

---

## Quick Start (CLI)

### Prerequisites

- Java 21 or later
- Maven 3.9+

### Build from source

```sh
git clone https://github.com/rydickin/truthcrawl.git
cd truthcrawl
mvn package -pl truthcrawl-core,truthcrawl-cli -DskipTests
```

### Run the CLI

After building, run commands via:

```sh
java -cp "truthcrawl-core/target/*:truthcrawl-cli/target/*" \
  io.truthcrawl.cli.Main <command> [args...]
```

Create an alias for convenience:

```sh
alias truthcrawl='java -cp "truthcrawl-core/target/*:truthcrawl-cli/target/*" io.truthcrawl.cli.Main'
```

### Generate keys

```sh
truthcrawl gen-key ~/truthcrawl-data/keys
```

This creates `priv.key` and `pub.key` in the specified directory.

---

## Running a Node

### Autonomous node (recommended)

The `node` command runs everything automatically:

```sh
truthcrawl node [data-dir]
```

This starts:
- **HTTP server** on port 8080 (serves dashboard, records, batches, peer API)
- **Sync loop** that pulls new batches and records from registered peers
- **Crawl loop** that observes URLs listed in `data-dir/urls.txt`

`data-dir` defaults to `~/truthcrawl-data` (or the `TRUTHCRAWL_DATA`
environment variable).

### Configuration

Configure the node via environment variables:

| Variable | Default | Description |
|---|---|---|
| `TRUTHCRAWL_DATA` | `~/truthcrawl-data` | Data directory path |
| `TRUTHCRAWL_PORT` | `8080` | HTTP server port |
| `TRUTHCRAWL_SYNC_INTERVAL` | `300` | Seconds between peer sync cycles |
| `TRUTHCRAWL_CRAWL_INTERVAL` | `3600` | Seconds between URL crawl cycles |

Docker example with custom config:

```sh
docker run -d \
  -p 9090:9090 \
  -e TRUTHCRAWL_PORT=9090 \
  -e TRUTHCRAWL_SYNC_INTERVAL=60 \
  -e TRUTHCRAWL_CRAWL_INTERVAL=1800 \
  -v truthcrawl-data:/data \
  rydickin/truthcrawl
```

### Data directory layout

The node creates and manages this structure:

```
truthcrawl-data/
  keys/
    priv.key              # Ed25519 private key
    pub.key               # Ed25519 public key
  store/
    ab/
      abcdef...1234.txt   # Observation records (sharded by hash prefix)
  batches/
    batch-2026-02-04-001/
      manifest.txt        # Record hashes in this batch
      chain-link.txt      # Merkle root + back-reference to previous batch
      signature.txt       # Publisher signature
  timestamps/
    abcdef...1234.txt     # Timestamp tokens (keyed by data hash)
  peers/
    abcdef...1234.txt     # Registered peer info
  profiles/
    abcdef...1234.txt     # Node identity profiles
  urls.txt                # URLs to auto-crawl (one per line)
```

---

## Multi-Node Setup

### On a local network

1. Start nodes on two machines. Find each machine's LAN IP:

   ```sh
   hostname -I
   ```

2. Register each node as a peer of the other. From node A:

   ```sh
   truthcrawl register-peer http://<node-B-ip>:8080 <node-B-pub-key> ~/truthcrawl-data/peers
   ```

   Get node B's public key from `http://<node-B-ip>:8080/info` or from its
   `pub.key` file.

3. Once peers are registered, the nodes sync automatically.

### Manual sync (one-time)

Pull all batches and records from a remote node without registering it as a
peer:

```sh
truthcrawl sync http://<remote-ip>:8080
```

Or with Docker:

```sh
docker exec <container-id> /app/truthcrawl sync http://<remote-ip>:8080
```

### Running a second node via Docker

On another machine:

```sh
docker run -d \
  -p 8080:8080 \
  -v truthcrawl-data:/data \
  rydickin/truthcrawl
```

Each node generates its own unique key pair and node ID on first run.

---

## CLI Commands

### Core workflow

| Command | Description |
|---|---|
| `node [data-dir]` | Run an autonomous node (server + sync + crawl) |
| `crawl <url> [data-dir]` | Crawl a URL, store, batch, and timestamp in one step |
| `sync [remote-url] [data-dir]` | Pull batches and records from peers |
| `gen-key <output-dir>` | Generate Ed25519 key pair |

### Server and networking

| Command | Description |
|---|---|
| `start-server <port> <store-dir> <batches-dir> <peers-dir> <priv-key> <pub-key>` | Start HTTP server only (no auto-sync/crawl) |
| `register-peer <endpoint-url> <peer-pub-key> <peers-dir>` | Register a remote peer |
| `list-peers <peers-dir>` | List registered peers |

### Observation and records

| Command | Description |
|---|---|
| `observe <url> <priv-key> <pub-key> <output-file>` | Crawl a URL and write a signed observation record |
| `store-record <record-file> <store-dir>` | Store a record in the hash-addressed store |
| `compare-records <original> <actual>` | Compare two observation records |
| `query-url <url> <store-dir>` | Find all records for a URL |
| `query-node <node-id> <store-dir>` | Find all records from a node |

### Batches and chain

| Command | Description |
|---|---|
| `build-root <manifest-file>` | Compute Merkle root from a manifest |
| `publish-chain-batch <batch-id> <manifest> <prev-root> <priv-key> <pub-key> <out-dir>` | Publish a chained batch |
| `verify-batch <metadata> <manifest> <signature> <pub-key>` | Verify a batch signature |
| `verify-chain <pub-key> <batches-dir> [expected-root]` | Verify the entire batch chain |
| `verify-proof <leaf-hex> <proof-file> <expected-root-hex>` | Verify a Merkle inclusion proof |
| `chain-stats <chain-length> <store-dir>` | Show chain statistics |
| `export-batch <manifest> <chain-link> <signature> <store-dir> <output-dir>` | Export a batch for offline transfer |
| `import-batch <export-dir> <store-dir> <pub-key>` | Import an exported batch |

### Timestamping

| Command | Description |
|---|---|
| `timestamp <data-hash> <tsa-priv-key> <tsa-pub-key> <timestamp-dir>` | Issue a timestamp token |
| `verify-timestamp <data-hash> <tsa-pub-key> <timestamp-dir>` | Verify a timestamp token |
| `timestamp-batch <chain-link-file> <tsa-priv-key> <tsa-pub-key> <timestamp-dir>` | Timestamp a batch chain link |

### Verification and auditing

| Command | Description |
|---|---|
| `verify-record <record> <node-pub-key> <manifest> <metadata>` | Verify a record's signature and inclusion |
| `verify-pipeline <batch-id> <manifest> <merkle-root> <seed> <store-dir> [verification-dir]` | Run the verification pipeline |
| `verification-status <batch-id> <verification-dir>` | Check verification status |
| `sample-observations <manifest> <seed> <sample-size> <store-dir>` | Sample records for re-verification |
| `audit-report <batch-id> <records-total> <records-verified> <records-passed> <records-failed>` | Generate an audit report |

### Disputes

| Command | Description |
|---|---|
| `file-dispute <dispute-id> <challenged-record> <challenger-record> <priv-key> <pub-key>` | File a dispute between conflicting records |
| `resolve-dispute <dispute-file> <observation-file-1> [observation-file-2...]` | Resolve a dispute by majority vote |
| `node-reputation <resolution-file-1> [resolution-file-2...]` | Compute node reputation from resolutions |

### Node identity

| Command | Description |
|---|---|
| `register-node <name> <org> <email> <priv-key> <pub-key> <profiles-dir>` | Register node operator identity |
| `attest-capabilities <priv-key> <pub-key> <profiles-dir> <domain1> [domain2...]` | Attest domains this node crawls |
| `node-profile <profiles-dir> [node-id]` | View node profiles |
| `verify-node <profile-file>` | Verify a node's identity and attestation |

### Content archival

| Command | Description |
|---|---|
| `archive-content <file> <content-type> <archive-dir>` | Archive content by hash |
| `retrieve-content <content-hash> <archive-dir>` | Retrieve archived content |
| `archive-stats <archive-dir>` | Show archive statistics |

---

## HTTP API

When running a node or server, the following endpoints are available:

| Method | Path | Description |
|---|---|---|
| GET | `/` | Node dashboard (HTML) |
| GET | `/info` | Node ID and public key |
| GET | `/records/{hash}` | Fetch a record by hash |
| GET | `/batches` | List all batch IDs |
| GET | `/batches/{id}/manifest` | Batch manifest |
| GET | `/batches/{id}/chain-link` | Batch chain link |
| GET | `/batches/{id}/signature` | Batch signature |
| GET | `/peers` | List registered peers |
| POST | `/peers` | Register as a peer (authenticated) |

---

## Verification

Every claim in truthcrawl is independently verifiable. Given only the public
keys and the published data, any third party can:

1. **Verify a record** -- check the node's Ed25519 signature
2. **Verify a batch** -- recompute the Merkle root from the manifest and check the publisher signature
3. **Verify the chain** -- walk the batch chain and confirm each link references the previous root
4. **Verify a timestamp** -- check the TSA signature over the data hash and time

No trust in the node operator is required. Only math.
