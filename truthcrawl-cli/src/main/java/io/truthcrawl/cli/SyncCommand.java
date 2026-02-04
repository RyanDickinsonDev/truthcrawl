package io.truthcrawl.cli;

import io.truthcrawl.core.ObservationRecord;
import io.truthcrawl.core.PeerInfo;
import io.truthcrawl.core.PeerRegistry;
import io.truthcrawl.core.PublisherKey;
import io.truthcrawl.core.RecordStore;
import io.truthcrawl.core.RequestSigner;
import io.truthcrawl.core.TruthcrawlClient;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * CLI command: sync.
 *
 * <p>Pulls all batches and records from remote nodes that we don't have locally.
 *
 * <p>Usage:
 * <pre>
 *   truthcrawl sync [data-dir]              — sync with all registered peers
 *   truthcrawl sync &lt;remote-url&gt; [data-dir] — sync with a specific node
 * </pre>
 *
 * <p>data-dir defaults to TRUTHCRAWL_DATA env var or ~/truthcrawl-data.
 *
 * <p>Exit codes: 0 success, 1 usage error, 2 runtime error.
 */
final class SyncCommand {

    private SyncCommand() {}

    static int run(String[] args) {
        if (args.length > 2) {
            System.err.println("Usage: truthcrawl sync [remote-url] [data-dir]");
            return 1;
        }

        // Figure out if first arg is a URL or a data-dir
        String remoteUrl = null;
        Path dataDir = null;

        if (args.length >= 1) {
            if (args[0].startsWith("http://") || args[0].startsWith("https://")) {
                remoteUrl = args[0];
                if (args.length == 2) {
                    dataDir = Path.of(args[1]);
                }
            } else {
                // First arg is data-dir, no explicit URL
                dataDir = Path.of(args[0]);
            }
        }

        if (dataDir == null) {
            String envDir = System.getenv("TRUTHCRAWL_DATA");
            if (envDir != null && !envDir.isEmpty()) {
                dataDir = Path.of(envDir);
            } else {
                dataDir = Path.of(System.getProperty("user.home"), "truthcrawl-data");
            }
        }

        Path storeDir = dataDir.resolve("store");
        Path batchesDir = dataDir.resolve("batches");
        Path peersDir = dataDir.resolve("peers");
        Path keysDir = dataDir.resolve("keys");

        try {
            String privKey = Files.readString(keysDir.resolve("priv.key"), StandardCharsets.UTF_8).strip();
            String pubKey = Files.readString(keysDir.resolve("pub.key"), StandardCharsets.UTF_8).strip();
            PublisherKey key = PublisherKey.fromKeyPair(pubKey, privKey);
            RequestSigner signer = new RequestSigner(key);
            TruthcrawlClient client = new TruthcrawlClient(signer);
            RecordStore store = new RecordStore(storeDir);

            // Build list of endpoints to sync with
            List<String> endpoints = new ArrayList<>();
            if (remoteUrl != null) {
                String url = remoteUrl.endsWith("/")
                        ? remoteUrl.substring(0, remoteUrl.length() - 1) : remoteUrl;
                endpoints.add(url);
            } else {
                // Sync with all registered peers
                PeerRegistry registry = new PeerRegistry(peersDir);
                List<String> peerIds = registry.listNodeIds();
                if (peerIds.isEmpty()) {
                    System.out.println("No peers registered. Use 'truthcrawl sync <url>' or register peers first.");
                    return 0;
                }
                for (String peerId : peerIds) {
                    PeerInfo peer = registry.load(peerId);
                    if (peer != null) {
                        String url = peer.endpointUrl();
                        if (url.endsWith("/")) {
                            url = url.substring(0, url.length() - 1);
                        }
                        endpoints.add(url);
                    }
                }
                System.out.println("Syncing with " + endpoints.size() + " peer(s)...");
            }

            int totalBatches = 0;
            int totalRecords = 0;

            for (String endpoint : endpoints) {
                try {
                    int[] result = syncFrom(endpoint, client, store, batchesDir);
                    totalBatches += result[0];
                    totalRecords += result[1];
                } catch (Exception e) {
                    System.err.println("  Failed to sync from " + endpoint + ": " + e.getMessage());
                }
            }

            System.out.println("Done. " + totalBatches + " new batches, " + totalRecords + " new records.");
            return 0;

        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            return 2;
        }
    }

    private static int[] syncFrom(String endpoint, TruthcrawlClient client,
                                   RecordStore store, Path batchesDir) throws Exception {
        String info = client.getInfo(endpoint);
        System.out.println("Connected to " + endpoint);
        for (String line : info.strip().split("\n")) {
            if (line.startsWith("node_id:")) {
                String nodeId = line.substring("node_id:".length());
                System.out.println("  Node: " + nodeId.substring(0, Math.min(16, nodeId.length())) + "...");
            }
        }

        String batchesText = client.listBatches(endpoint);
        List<String> remoteBatches = batchesText.strip().lines()
                .filter(s -> !s.isEmpty())
                .toList();

        int newBatches = 0;
        int newRecords = 0;

        for (String batchId : remoteBatches) {
            Path localBatchDir = batchesDir.resolve("batch-" + batchId);
            if (Files.exists(localBatchDir)) {
                continue;
            }

            String manifest = client.getManifest(endpoint, batchId);
            String chainLink = client.getChainLink(endpoint, batchId);
            String signature = client.getSignature(endpoint, batchId);

            if (manifest == null || chainLink == null || signature == null) {
                System.err.println("  Skipping " + batchId + " (incomplete on remote)");
                continue;
            }

            List<String> recordHashes = manifest.strip().lines()
                    .filter(s -> !s.isEmpty())
                    .toList();

            for (String hash : recordHashes) {
                if (store.contains(hash)) {
                    continue;
                }
                String recordText = client.getRecord(endpoint, hash);
                if (recordText != null) {
                    ObservationRecord record = ObservationRecord.parse(
                            List.of(recordText.split("\n")));
                    store.store(record);
                    newRecords++;
                }
            }

            Files.createDirectories(localBatchDir);
            Files.writeString(localBatchDir.resolve("manifest.txt"), manifest, StandardCharsets.UTF_8);
            Files.writeString(localBatchDir.resolve("chain-link.txt"), chainLink, StandardCharsets.UTF_8);
            Files.writeString(localBatchDir.resolve("signature.txt"), signature, StandardCharsets.UTF_8);
            newBatches++;

            System.out.println("  Synced batch " + batchId + " (" + recordHashes.size() + " records)");
        }

        return new int[]{newBatches, newRecords};
    }
}
