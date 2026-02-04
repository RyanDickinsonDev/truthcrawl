package io.truthcrawl.cli;

import io.truthcrawl.core.BatchManifest;
import io.truthcrawl.core.BatchMetadata;
import io.truthcrawl.core.PublisherKey;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * CLI command: publish-batch.
 *
 * <p>Reads a manifest file and a private key, produces a signed batch.
 * Writes three output files to the output directory:
 * <ul>
 *   <li>manifest.txt — canonical manifest</li>
 *   <li>metadata.txt — batch metadata (4 key:value lines)</li>
 *   <li>signature.txt — Base64 Ed25519 signature</li>
 * </ul>
 *
 * <p>Usage: truthcrawl publish-batch &lt;batch-id&gt; &lt;manifest-file&gt; &lt;private-key-file&gt; &lt;public-key-file&gt; &lt;output-dir&gt;
 *
 * <p>Exit codes: 0 success, 1 usage error, 2 input error.
 */
final class PublishBatchCommand {

    private PublishBatchCommand() {}

    static int run(String[] args) {
        if (args.length != 5) {
            System.err.println(
                    "Usage: truthcrawl publish-batch <batch-id> <manifest-file> <private-key-file> <public-key-file> <output-dir>");
            return 1;
        }

        String batchId = args[0];
        Path manifestPath = Path.of(args[1]);
        Path privateKeyPath = Path.of(args[2]);
        Path publicKeyPath = Path.of(args[3]);
        Path outputDir = Path.of(args[4]);

        try {
            // Read manifest
            List<String> manifestLines = Files.readAllLines(manifestPath, StandardCharsets.UTF_8);
            BatchManifest manifest = BatchManifest.parse(manifestLines);

            // Read keys
            String privateKeyBase64 = Files.readString(privateKeyPath, StandardCharsets.UTF_8).strip();
            String publicKeyBase64 = Files.readString(publicKeyPath, StandardCharsets.UTF_8).strip();
            PublisherKey key = PublisherKey.fromKeyPair(publicKeyBase64, privateKeyBase64);

            // Build metadata
            BatchMetadata metadata = BatchMetadata.fromManifest(batchId, manifest);

            // Sign
            String signature = key.sign(metadata.signingInput());

            // Write outputs
            Files.createDirectories(outputDir);
            Files.writeString(outputDir.resolve("manifest.txt"),
                    manifest.toCanonicalText(), StandardCharsets.UTF_8);
            Files.writeString(outputDir.resolve("metadata.txt"),
                    metadata.toCanonicalText(), StandardCharsets.UTF_8);
            Files.writeString(outputDir.resolve("signature.txt"),
                    signature + "\n", StandardCharsets.UTF_8);

            System.out.println("batch_id:" + metadata.batchId());
            System.out.println("merkle_root:" + metadata.merkleRoot());
            System.out.println("record_count:" + metadata.recordCount());
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
