package io.truthcrawl.cli;

import io.truthcrawl.core.BatchManifest;
import io.truthcrawl.core.ChainLink;
import io.truthcrawl.core.NodeSigner;
import io.truthcrawl.core.ObservationRecord;
import io.truthcrawl.core.PublisherKey;
import io.truthcrawl.core.RecordStore;
import io.truthcrawl.core.TimestampAuthority;
import io.truthcrawl.core.TimestampStore;
import io.truthcrawl.core.TimestampToken;

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
 * CLI command: crawl.
 *
 * <p>All-in-one command that observes a URL, stores the record, publishes a
 * chained batch, and timestamps it. Automatically finds the previous batch
 * root and generates a batch ID.
 *
 * <p>Usage: truthcrawl crawl &lt;url&gt; &lt;data-dir&gt;
 *
 * <p>Expected data-dir layout:
 * <pre>
 *   data-dir/keys/priv.key
 *   data-dir/keys/pub.key
 *   data-dir/store/          (record store)
 *   data-dir/batches/        (batch-{id}/ subdirectories)
 *   data-dir/timestamps/     (timestamp tokens)
 * </pre>
 *
 * <p>Exit codes: 0 success, 1 usage error, 2 runtime error.
 */
final class CrawlCommand {

    private CrawlCommand() {}

    static int run(String[] args) {
        if (args.length < 1 || args.length > 2) {
            System.err.println("Usage: truthcrawl crawl <url> [data-dir]");
            System.err.println("  data-dir defaults to ~/truthcrawl-data");
            return 1;
        }

        String url = args[0];
        Path dataDir;
        if (args.length == 2) {
            dataDir = Path.of(args[1]);
        } else {
            String envDir = System.getenv("TRUTHCRAWL_DATA");
            if (envDir != null && !envDir.isEmpty()) {
                dataDir = Path.of(envDir);
            } else {
                dataDir = Path.of(System.getProperty("user.home"), "truthcrawl-data");
            }
        }

        Path privKeyPath = dataDir.resolve("keys/priv.key");
        Path pubKeyPath = dataDir.resolve("keys/pub.key");
        Path storeDir = dataDir.resolve("store");
        Path batchesDir = dataDir.resolve("batches");
        Path timestampsDir = dataDir.resolve("timestamps");

        try {
            // Check keys exist
            if (!Files.exists(privKeyPath) || !Files.exists(pubKeyPath)) {
                System.err.println("Error: keys not found in " + dataDir.resolve("keys"));
                System.err.println("Run 'truthcrawl gen-key " + dataDir.resolve("keys") + "' first.");
                return 2;
            }

            String privKey = Files.readString(privKeyPath, StandardCharsets.UTF_8).strip();
            String pubKey = Files.readString(pubKeyPath, StandardCharsets.UTF_8).strip();
            PublisherKey key = PublisherKey.fromKeyPair(pubKey, privKey);
            NodeSigner signer = NodeSigner.fromKeyPair(key);

            // 1. Observe
            System.out.println("Crawling " + url + " ...");
            ObservationRecord unsigned = HttpObserver.observe(url, signer.nodeId());
            ObservationRecord signed = signer.sign(unsigned);

            // 2. Store
            RecordStore store = new RecordStore(storeDir);
            String recordHash = store.store(signed);
            System.out.println("Stored   " + recordHash);

            // 3. Find previous root
            String previousRoot = findLatestRoot(batchesDir);

            // 4. Auto-generate batch ID
            String batchId = generateBatchId(batchesDir);

            // 5. Publish chained batch
            BatchManifest manifest = BatchManifest.parse(List.of(recordHash));
            ChainLink link = ChainLink.fromManifest(batchId, manifest, previousRoot);
            String signature = key.sign(link.signingInput());

            Path batchDir = batchesDir.resolve("batch-" + batchId);
            Files.createDirectories(batchDir);
            Files.writeString(batchDir.resolve("manifest.txt"),
                    manifest.toCanonicalText(), StandardCharsets.UTF_8);
            Files.writeString(batchDir.resolve("chain-link.txt"),
                    link.toCanonicalText(), StandardCharsets.UTF_8);
            Files.writeString(batchDir.resolve("signature.txt"),
                    signature + "\n", StandardCharsets.UTF_8);
            System.out.println("Batched  " + batchId + " (root: " + link.merkleRoot().substring(0, 16) + "...)");

            // 6. Timestamp
            TimestampAuthority tsa = new TimestampAuthority(key);
            TimestampStore tsStore = new TimestampStore(timestampsDir);
            TimestampToken token = tsa.issue(recordHash);
            tsStore.store(token);
            System.out.println("Stamped  " + token.issuedAt());

            System.out.println("Done.");
            return 0;

        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            return 2;
        }
    }

    /**
     * Find the merkle_root of the latest batch, or genesis root if no batches exist.
     */
    private static String findLatestRoot(Path batchesDir) throws Exception {
        if (!Files.exists(batchesDir)) {
            return ChainLink.GENESIS_ROOT;
        }

        List<String> batchNames = new ArrayList<>();
        try (Stream<Path> dirs = Files.list(batchesDir)) {
            dirs.filter(Files::isDirectory)
                    .filter(d -> d.getFileName().toString().startsWith("batch-"))
                    .forEach(d -> batchNames.add(d.getFileName().toString()));
        }

        if (batchNames.isEmpty()) {
            return ChainLink.GENESIS_ROOT;
        }

        Collections.sort(batchNames);
        String latest = batchNames.get(batchNames.size() - 1);
        Path chainLinkFile = batchesDir.resolve(latest).resolve("chain-link.txt");

        if (!Files.exists(chainLinkFile)) {
            return ChainLink.GENESIS_ROOT;
        }

        List<String> lines = Files.readAllLines(chainLinkFile, StandardCharsets.UTF_8);
        ChainLink link = ChainLink.parse(lines);
        return link.merkleRoot();
    }

    /**
     * Generate a batch ID like 2026-02-04-001, auto-incrementing the suffix.
     */
    private static String generateBatchId(Path batchesDir) throws Exception {
        String datePrefix = LocalDate.now(ZoneOffset.UTC).toString();

        if (!Files.exists(batchesDir)) {
            return datePrefix + "-001";
        }

        int maxSeq = 0;
        try (Stream<Path> dirs = Files.list(batchesDir)) {
            List<Path> matching = dirs
                    .filter(Files::isDirectory)
                    .filter(d -> d.getFileName().toString().startsWith("batch-" + datePrefix))
                    .toList();

            for (Path d : matching) {
                String name = d.getFileName().toString();
                String suffix = name.substring(("batch-" + datePrefix).length());
                if (suffix.isEmpty()) {
                    // Old-style batch without sequence number
                    maxSeq = Math.max(maxSeq, 0);
                } else if (suffix.startsWith("-")) {
                    try {
                        int seq = Integer.parseInt(suffix.substring(1));
                        maxSeq = Math.max(maxSeq, seq);
                    } catch (NumberFormatException ignored) {}
                }
            }
        }

        return datePrefix + "-" + String.format("%03d", maxSeq + 1);
    }
}
