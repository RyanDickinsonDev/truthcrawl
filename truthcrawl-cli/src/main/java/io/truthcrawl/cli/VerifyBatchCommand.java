package io.truthcrawl.cli;

import io.truthcrawl.core.BatchManifest;
import io.truthcrawl.core.BatchMetadata;
import io.truthcrawl.core.BatchVerifier;
import io.truthcrawl.core.PublisherKey;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * CLI command: verify-batch.
 *
 * <p>Verifies a published batch: checks signature, manifest hash, Merkle root, and record count.
 *
 * <p>Usage: truthcrawl verify-batch &lt;metadata-file&gt; &lt;manifest-file&gt; &lt;signature-file&gt; &lt;public-key-file&gt;
 *
 * <p>Exit codes: 0 valid, 1 usage error, 2 input error, 3 verification failed.
 */
final class VerifyBatchCommand {

    private VerifyBatchCommand() {}

    static int run(String[] args) {
        if (args.length != 4) {
            System.err.println(
                    "Usage: truthcrawl verify-batch <metadata-file> <manifest-file> <signature-file> <public-key-file>");
            return 1;
        }

        Path metadataPath = Path.of(args[0]);
        Path manifestPath = Path.of(args[1]);
        Path signaturePath = Path.of(args[2]);
        Path publicKeyPath = Path.of(args[3]);

        try {
            // Read inputs
            List<String> metadataLines = Files.readAllLines(metadataPath, StandardCharsets.UTF_8);
            BatchMetadata metadata = BatchMetadata.parse(metadataLines);

            List<String> manifestLines = Files.readAllLines(manifestPath, StandardCharsets.UTF_8);
            BatchManifest manifest = BatchManifest.parse(manifestLines);

            String signature = Files.readString(signaturePath, StandardCharsets.UTF_8).strip();
            String publicKeyBase64 = Files.readString(publicKeyPath, StandardCharsets.UTF_8).strip();
            PublisherKey key = PublisherKey.fromPublicKey(publicKeyBase64);

            // Verify
            BatchVerifier.Result result = BatchVerifier.verify(metadata, manifest, signature, key);

            if (result.valid()) {
                System.out.println("PASS");
                return 0;
            } else {
                System.out.println("FAIL");
                for (String error : result.errors()) {
                    System.err.println("  " + error);
                }
                return 3;
            }

        } catch (IOException e) {
            System.err.println("I/O error: " + e.getMessage());
            return 2;
        } catch (IllegalArgumentException e) {
            System.err.println("Invalid input: " + e.getMessage());
            return 2;
        }
    }
}
