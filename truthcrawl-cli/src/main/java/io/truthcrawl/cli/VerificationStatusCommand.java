package io.truthcrawl.cli;

import io.truthcrawl.core.VerificationStatus;

import java.nio.file.Path;

/**
 * CLI command: verification-status.
 *
 * <p>Displays the verification status for a batch.
 *
 * <p>Usage: truthcrawl verification-status &lt;batch-id&gt; &lt;verification-dir&gt;
 *
 * <p>Exit codes: 0 success (found), 1 usage error, 2 not found or read error.
 */
final class VerificationStatusCommand {

    private VerificationStatusCommand() {}

    static int run(String[] args) {
        if (args.length != 2) {
            System.err.println("Usage: truthcrawl verification-status <batch-id> <verification-dir>");
            return 1;
        }

        String batchId = args[0];
        Path verificationDir = Path.of(args[1]);

        try {
            VerificationStatus status = VerificationStatus.load(verificationDir, batchId);
            if (status == null) {
                System.err.println("No verification status found for batch: " + batchId);
                return 2;
            }

            System.out.print(status.toCanonicalText());
            return 0;

        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            return 2;
        }
    }
}
