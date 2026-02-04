#!/bin/sh
set -e

DATA="${TRUTHCRAWL_DATA:-/data}"
mkdir -p "$DATA/keys" "$DATA/store" "$DATA/batches" "$DATA/peers" "$DATA/timestamps" "$DATA/profiles"

# Auto-generate keys on first run
if [ ! -f "$DATA/keys/pub.key" ]; then
    echo "Generating node keys..."
    /app/truthcrawl gen-key "$DATA/keys"
fi

# Default: run the full node
case "${1:-node}" in
    node)
        exec /app/truthcrawl node "$DATA"
        ;;
    crawl|sync|observe|store-record|verify-*|register-*|attest-*|node-profile|gen-key|timestamp*|archive-*|retrieve-*|query-*|chain-*|export-*|import-*|build-root|publish-*|compare-*|file-dispute|resolve-dispute|sample-*|audit-*|list-peers|start-server)
        exec /app/truthcrawl "$@"
        ;;
    *)
        exec "$@"
        ;;
esac
