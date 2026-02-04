package io.truthcrawl.cli;

import io.truthcrawl.core.BatchManifest;
import io.truthcrawl.core.RecordStore;
import io.truthcrawl.core.VerificationPipeline;
import io.truthcrawl.core.VerificationStatus;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;

/**
 * CLI command: verify-pipeline.
 *
 * <p>Runs the full verification pipeline on an imported batch.
 *
 * <p>Usage: truthcrawl verify-pipeline &lt;batch-id&gt; &lt;manifest&gt; &lt;merkle-root&gt; &lt;seed&gt; &lt;store-dir&gt; [verification-dir]
 *
 * <p>Outputs verification status. If verification-dir is provided, saves status to file.
 *
 * <p>Exit codes: 0 success, 1 usage error, 2 input error.
 */
final class VerifyPipelineCommand {

    private VerifyPipelineCommand() {}

    static int run(String[] args) {
        if (args.length < 5 || args.length > 6) {
            System.err.println("Usage: truthcrawl verify-pipeline <batch-id> <manifest> <merkle-root> <seed> <store-dir> [verification-dir]");
            return 1;
        }

        String batchId = args[0];
        Path manifestPath = Path.of(args[1]);
        String merkleRoot = args[2];
        String userSeed = args[3];
        Path storeDir = Path.of(args[4]);
        Path verificationDir = args.length == 6 ? Path.of(args[5]) : null;

        try {
            BatchManifest manifest = BatchManifest.parse(
                    Files.readAllLines(manifestPath, StandardCharsets.UTF_8));
            RecordStore store = new RecordStore(storeDir);

            VerificationPipeline.PipelineResult result =
                    VerificationPipeline.run(batchId, manifest, merkleRoot, userSeed, store);

            VerificationStatus status = VerificationStatus.fromPipelineResult(
                    result, Instant.now());

            System.out.print(status.toCanonicalText());

            if (verificationDir != null) {
                status.save(verificationDir);
            }

            return 0;

        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            return 2;
        }
    }
}
