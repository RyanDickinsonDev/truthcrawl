package io.truthcrawl.cli;

import io.truthcrawl.core.BatchManifest;
import io.truthcrawl.core.ChainLink;
import io.truthcrawl.core.NodeSigner;
import io.truthcrawl.core.ObservationRecord;
import io.truthcrawl.core.PeerInfo;
import io.truthcrawl.core.PeerRegistry;
import io.truthcrawl.core.PublisherKey;
import io.truthcrawl.core.RecordStore;
import io.truthcrawl.core.RequestSigner;
import io.truthcrawl.core.TimestampAuthority;
import io.truthcrawl.core.TimestampStore;
import io.truthcrawl.core.TimestampToken;
import io.truthcrawl.core.TruthcrawlClient;
import io.truthcrawl.core.TruthcrawlServer;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;

/**
 * CLI command: node.
 *
 * <p>Runs a full truthcrawl node: HTTP server + auto-sync + auto-crawl.
 *
 * <p>Usage: truthcrawl node [data-dir]
 *
 * <p>data-dir defaults to TRUTHCRAWL_DATA env var or ~/truthcrawl-data.
 *
 * <p>Configuration via environment variables:
 * <ul>
 *   <li>TRUTHCRAWL_PORT — server port (default: 8080)</li>
 *   <li>TRUTHCRAWL_SYNC_INTERVAL — seconds between sync cycles (default: 300)</li>
 *   <li>TRUTHCRAWL_CRAWL_INTERVAL — seconds between crawl cycles (default: 3600)</li>
 * </ul>
 *
 * <p>Place URLs to crawl in data-dir/urls.txt (one per line).
 *
 * <p>Exit codes: 0 success (on shutdown), 1 usage error, 2 runtime error.
 */
final class NodeCommand {

    private NodeCommand() {}

    static int run(String[] args) {
        if (args.length > 1) {
            System.err.println("Usage: truthcrawl node [data-dir]");
            return 1;
        }

        Path dataDir;
        if (args.length == 1) {
            dataDir = Path.of(args[0]);
        } else {
            String envDir = System.getenv("TRUTHCRAWL_DATA");
            if (envDir != null && !envDir.isEmpty()) {
                dataDir = Path.of(envDir);
            } else {
                dataDir = Path.of(System.getProperty("user.home"), "truthcrawl-data");
            }
        }

        int port = intEnv("TRUTHCRAWL_PORT", 8080);
        int syncInterval = intEnv("TRUTHCRAWL_SYNC_INTERVAL", 300);
        int crawlInterval = intEnv("TRUTHCRAWL_CRAWL_INTERVAL", 3600);

        Path keysDir = dataDir.resolve("keys");
        Path storeDir = dataDir.resolve("store");
        Path batchesDir = dataDir.resolve("batches");
        Path peersDir = dataDir.resolve("peers");
        Path timestampsDir = dataDir.resolve("timestamps");
        Path urlsFile = dataDir.resolve("urls.txt");

        try {
            // Ensure directories exist
            Files.createDirectories(keysDir);
            Files.createDirectories(storeDir);
            Files.createDirectories(batchesDir);
            Files.createDirectories(peersDir);
            Files.createDirectories(timestampsDir);

            // Auto-generate keys if needed
            if (!Files.exists(keysDir.resolve("pub.key"))) {
                System.out.println("Generating node keys...");
                GenKeyCommand.run(new String[]{keysDir.toString()});
            }

            String privKey = Files.readString(keysDir.resolve("priv.key"), StandardCharsets.UTF_8).strip();
            String pubKey = Files.readString(keysDir.resolve("pub.key"), StandardCharsets.UTF_8).strip();
            PublisherKey key = PublisherKey.fromKeyPair(pubKey, privKey);
            String nodeId = RequestSigner.computeNodeId(key);

            RecordStore recordStore = new RecordStore(storeDir);
            PeerRegistry peerRegistry = new PeerRegistry(peersDir);

            // Start HTTP server
            InetSocketAddress address = new InetSocketAddress("0.0.0.0", port);
            TruthcrawlServer server = new TruthcrawlServer(
                    address, recordStore, batchesDir, peerRegistry, key);
            server.start();

            System.out.println("truthcrawl node started");
            System.out.println("  node_id:  " + nodeId.substring(0, 16) + "...");
            System.out.println("  server:   http://0.0.0.0:" + port);
            System.out.println("  data:     " + dataDir);
            System.out.println("  sync:     every " + syncInterval + "s");
            System.out.println("  crawl:    every " + crawlInterval + "s");
            if (Files.exists(urlsFile)) {
                long urlCount = Files.readAllLines(urlsFile).stream()
                        .filter(l -> !l.strip().isEmpty() && !l.strip().startsWith("#"))
                        .count();
                System.out.println("  urls:     " + urlCount + " in urls.txt");
            } else {
                System.out.println("  urls:     none (create " + urlsFile + " to auto-crawl)");
            }

            // Sync thread
            Thread syncThread = new Thread(() -> {
                while (!Thread.currentThread().isInterrupted()) {
                    try {
                        Thread.sleep(syncInterval * 1000L);
                        syncAll(key, peerRegistry, recordStore, batchesDir);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } catch (Exception e) {
                        System.err.println("[sync] Error: " + e.getMessage());
                    }
                }
            }, "truthcrawl-sync");
            syncThread.setDaemon(true);
            syncThread.start();

            // Crawl thread
            Thread crawlThread = new Thread(() -> {
                while (!Thread.currentThread().isInterrupted()) {
                    try {
                        Thread.sleep(crawlInterval * 1000L);
                        crawlUrls(urlsFile, key, recordStore, batchesDir, timestampsDir);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } catch (Exception e) {
                        System.err.println("[crawl] Error: " + e.getMessage());
                    }
                }
            }, "truthcrawl-crawl");
            crawlThread.setDaemon(true);
            crawlThread.start();

            // Block until interrupted
            Thread.currentThread().join();
            return 0;

        } catch (InterruptedException e) {
            System.out.println("Node stopped.");
            return 0;
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            return 2;
        }
    }

    private static void syncAll(PublisherKey key, PeerRegistry registry,
                                 RecordStore store, Path batchesDir) {
        try {
            List<String> peerIds = registry.listNodeIds();
            if (peerIds.isEmpty()) return;

            RequestSigner signer = new RequestSigner(key);
            TruthcrawlClient client = new TruthcrawlClient(signer);

            for (String peerId : peerIds) {
                PeerInfo peer = registry.load(peerId);
                if (peer == null) continue;

                String endpoint = peer.endpointUrl();
                if (endpoint.endsWith("/")) {
                    endpoint = endpoint.substring(0, endpoint.length() - 1);
                }

                try {
                    int[] result = syncFrom(endpoint, client, store, batchesDir);
                    if (result[0] > 0 || result[1] > 0) {
                        System.out.println("[sync] " + endpoint + ": "
                                + result[0] + " batches, " + result[1] + " records");
                    }
                } catch (Exception e) {
                    // Peer offline — no big deal, try next time
                }
            }
        } catch (Exception e) {
            System.err.println("[sync] Error: " + e.getMessage());
        }
    }

    private static int[] syncFrom(String endpoint, TruthcrawlClient client,
                                    RecordStore store, Path batchesDir) throws Exception {
        String batchesText = client.listBatches(endpoint);
        List<String> remoteBatches = batchesText.strip().lines()
                .filter(s -> !s.isEmpty()).toList();

        int newBatches = 0, newRecords = 0;

        for (String batchId : remoteBatches) {
            Path localBatchDir = batchesDir.resolve("batch-" + batchId);
            if (Files.exists(localBatchDir)) continue;

            String manifest = client.getManifest(endpoint, batchId);
            String chainLink = client.getChainLink(endpoint, batchId);
            String signature = client.getSignature(endpoint, batchId);
            if (manifest == null || chainLink == null || signature == null) continue;

            List<String> recordHashes = manifest.strip().lines()
                    .filter(s -> !s.isEmpty()).toList();

            for (String hash : recordHashes) {
                if (store.contains(hash)) continue;
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
        }

        return new int[]{newBatches, newRecords};
    }

    private static void crawlUrls(Path urlsFile, PublisherKey key,
                                    RecordStore store, Path batchesDir,
                                    Path timestampsDir) {
        try {
            if (!Files.exists(urlsFile)) return;

            List<String> urls = Files.readAllLines(urlsFile, StandardCharsets.UTF_8).stream()
                    .map(String::strip)
                    .filter(l -> !l.isEmpty() && !l.startsWith("#"))
                    .toList();

            if (urls.isEmpty()) return;

            NodeSigner signer = NodeSigner.fromKeyPair(key);
            TimestampAuthority tsa = new TimestampAuthority(key);
            TimestampStore tsStore = new TimestampStore(timestampsDir);
            List<String> hashes = new ArrayList<>();

            for (String url : urls) {
                try {
                    ObservationRecord unsigned = HttpObserver.observe(url, signer.nodeId());
                    ObservationRecord signed = signer.sign(unsigned);
                    String hash = store.store(signed);
                    hashes.add(hash);

                    TimestampToken token = tsa.issue(hash);
                    tsStore.store(token);
                } catch (Exception e) {
                    System.err.println("[crawl] Failed: " + url + " — " + e.getMessage());
                }
            }

            if (hashes.isEmpty()) return;

            // Publish batch
            String previousRoot = findLatestRoot(batchesDir);
            String batchId = generateBatchId(batchesDir);
            BatchManifest manifest = BatchManifest.parse(hashes);
            ChainLink link = ChainLink.fromManifest(batchId, manifest, previousRoot);
            String signature = key.sign(link.signingInput());

            Path batchDir = batchesDir.resolve("batch-" + batchId);
            Files.createDirectories(batchDir);
            Files.writeString(batchDir.resolve("manifest.txt"), manifest.toCanonicalText(), StandardCharsets.UTF_8);
            Files.writeString(batchDir.resolve("chain-link.txt"), link.toCanonicalText(), StandardCharsets.UTF_8);
            Files.writeString(batchDir.resolve("signature.txt"), signature + "\n", StandardCharsets.UTF_8);

            System.out.println("[crawl] Batch " + batchId + ": "
                    + hashes.size() + " records from " + urls.size() + " URLs");

        } catch (Exception e) {
            System.err.println("[crawl] Error: " + e.getMessage());
        }
    }

    private static String findLatestRoot(Path batchesDir) throws Exception {
        if (!Files.exists(batchesDir)) return ChainLink.GENESIS_ROOT;

        List<String> batchNames = new ArrayList<>();
        try (Stream<Path> dirs = Files.list(batchesDir)) {
            dirs.filter(Files::isDirectory)
                    .filter(d -> d.getFileName().toString().startsWith("batch-"))
                    .forEach(d -> batchNames.add(d.getFileName().toString()));
        }
        if (batchNames.isEmpty()) return ChainLink.GENESIS_ROOT;

        Collections.sort(batchNames);
        Path chainLinkFile = batchesDir.resolve(batchNames.get(batchNames.size() - 1)).resolve("chain-link.txt");
        if (!Files.exists(chainLinkFile)) return ChainLink.GENESIS_ROOT;

        List<String> lines = Files.readAllLines(chainLinkFile, StandardCharsets.UTF_8);
        return ChainLink.parse(lines).merkleRoot();
    }

    private static String generateBatchId(Path batchesDir) throws Exception {
        String datePrefix = LocalDate.now(ZoneOffset.UTC).toString();
        if (!Files.exists(batchesDir)) return datePrefix + "-001";

        int maxSeq = 0;
        try (Stream<Path> dirs = Files.list(batchesDir)) {
            for (Path d : dirs.filter(Files::isDirectory)
                    .filter(p -> p.getFileName().toString().startsWith("batch-" + datePrefix))
                    .toList()) {
                String suffix = d.getFileName().toString().substring(("batch-" + datePrefix).length());
                if (suffix.startsWith("-")) {
                    try {
                        maxSeq = Math.max(maxSeq, Integer.parseInt(suffix.substring(1)));
                    } catch (NumberFormatException ignored) {}
                }
            }
        }
        return datePrefix + "-" + String.format("%03d", maxSeq + 1);
    }

    private static int intEnv(String name, int defaultValue) {
        String val = System.getenv(name);
        if (val == null || val.isEmpty()) return defaultValue;
        try {
            return Integer.parseInt(val);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }
}
