package io.truthcrawl.cli;

import io.truthcrawl.core.BatchManifest;
import io.truthcrawl.core.ChainLink;
import io.truthcrawl.core.PublisherKey;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * CLI command: publish-chain-batch.
 *
 * <p>Like publish-batch, but includes a previous_root back-reference to form a chain.
 * Writes three files: manifest.txt, chain-link.txt, signature.txt.
 *
 * <p>Usage: truthcrawl publish-chain-batch &lt;batch-id&gt; &lt;manifest-file&gt;
 *         &lt;priv-key&gt; &lt;pub-key&gt; &lt;previous-root&gt; &lt;output-dir&gt;
 *
 * <p>For the genesis batch, use "genesis" as the previous-root argument.
 *
 * <p>Exit codes: 0 success, 1 usage error, 2 input error.
 */
final class PublishChainBatchCommand {

    private PublishChainBatchCommand() {}

    static int run(String[] args) {
        if (args.length != 6) {
            System.err.println("Usage: truthcrawl publish-chain-batch <batch-id> <manifest-file>"
                    + " <priv-key> <pub-key> <previous-root> <output-dir>");
            return 1;
        }

        String batchId = args[0];
        Path manifestPath = Path.of(args[1]);
        Path privateKeyPath = Path.of(args[2]);
        Path publicKeyPath = Path.of(args[3]);
        String previousRoot = args[4];
        Path outputDir = Path.of(args[5]);

        if ("genesis".equals(previousRoot)) {
            previousRoot = ChainLink.GENESIS_ROOT;
        }

        try {
            List<String> manifestLines = Files.readAllLines(manifestPath, StandardCharsets.UTF_8);
            BatchManifest manifest = BatchManifest.parse(manifestLines);

            String privateKeyBase64 = Files.readString(privateKeyPath, StandardCharsets.UTF_8).strip();
            String publicKeyBase64 = Files.readString(publicKeyPath, StandardCharsets.UTF_8).strip();
            PublisherKey key = PublisherKey.fromKeyPair(publicKeyBase64, privateKeyBase64);

            ChainLink link = ChainLink.fromManifest(batchId, manifest, previousRoot);
            String signature = key.sign(link.signingInput());

            Files.createDirectories(outputDir);
            Files.writeString(outputDir.resolve("manifest.txt"),
                    manifest.toCanonicalText(), StandardCharsets.UTF_8);
            Files.writeString(outputDir.resolve("chain-link.txt"),
                    link.toCanonicalText(), StandardCharsets.UTF_8);
            Files.writeString(outputDir.resolve("signature.txt"),
                    signature + "\n", StandardCharsets.UTF_8);

            System.out.println("batch_id:" + link.batchId());
            System.out.println("merkle_root:" + link.merkleRoot());
            System.out.println("record_count:" + link.recordCount());
            System.out.println("previous_root:" + link.previousRoot());
            return 0;

        } catch (IOException e) {
            System.err.println("I/O error: " + e.getMessage());
            return 2;
        } catch (IllegalArgumentException e) {
            System.err.println("Invalid input: " + e.getMessage());
            return 2;
        }
    }
}
