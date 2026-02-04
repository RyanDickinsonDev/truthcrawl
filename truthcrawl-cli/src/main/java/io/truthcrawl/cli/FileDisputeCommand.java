package io.truthcrawl.cli;

import io.truthcrawl.core.DisputeRecord;
import io.truthcrawl.core.NodeSigner;
import io.truthcrawl.core.ObservationRecord;
import io.truthcrawl.core.PublisherKey;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;

/**
 * CLI command: file-dispute.
 *
 * <p>Creates and signs a dispute record challenging a published observation.
 *
 * <p>Usage: truthcrawl file-dispute &lt;dispute-id&gt; &lt;challenged-record&gt;
 *         &lt;challenger-record&gt; &lt;priv-key&gt; &lt;pub-key&gt; &lt;output-file&gt;
 *
 * <p>Exit codes: 0 success, 1 usage error, 2 input error.
 */
final class FileDisputeCommand {

    private FileDisputeCommand() {}

    static int run(String[] args) {
        if (args.length != 6) {
            System.err.println("Usage: truthcrawl file-dispute <dispute-id> <challenged-record>"
                    + " <challenger-record> <priv-key> <pub-key> <output-file>");
            return 1;
        }

        String disputeId = args[0];
        Path challengedPath = Path.of(args[1]);
        Path challengerPath = Path.of(args[2]);
        Path privKeyPath = Path.of(args[3]);
        Path pubKeyPath = Path.of(args[4]);
        Path outputPath = Path.of(args[5]);

        try {
            ObservationRecord challenged = ObservationRecord.parse(
                    Files.readAllLines(challengedPath, StandardCharsets.UTF_8));
            ObservationRecord challenger = ObservationRecord.parse(
                    Files.readAllLines(challengerPath, StandardCharsets.UTF_8));

            if (!challenged.url().equals(challenger.url())) {
                System.err.println("Records must be for the same URL");
                return 2;
            }

            String privKeyBase64 = Files.readString(privKeyPath, StandardCharsets.UTF_8).strip();
            String pubKeyBase64 = Files.readString(pubKeyPath, StandardCharsets.UTF_8).strip();
            PublisherKey key = PublisherKey.fromKeyPair(pubKeyBase64, privKeyBase64);
            String nodeId = NodeSigner.computeNodeId(pubKeyBase64);

            DisputeRecord dispute = new DisputeRecord(
                    disputeId,
                    challenged.recordHash(),
                    challenger.recordHash(),
                    challenged.url(),
                    Instant.now(),
                    nodeId,
                    null
            );

            String signature = key.sign(
                    dispute.toCanonicalText().getBytes(StandardCharsets.UTF_8));
            dispute = dispute.withSignature(signature);

            Files.writeString(outputPath, dispute.toFullText(), StandardCharsets.UTF_8);

            System.out.print(dispute.disputeHash());
            return 0;

        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            return 2;
        }
    }
}
