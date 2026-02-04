package io.truthcrawl.cli;

import io.truthcrawl.core.BatchManifest;
import io.truthcrawl.core.BatchMetadata;
import io.truthcrawl.core.ObservationRecord;
import io.truthcrawl.core.RecordInclusionVerifier;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * CLI command: verify-record.
 *
 * <p>Verifies a signed record's node signature and its inclusion in a batch.
 *
 * <p>Usage: truthcrawl verify-record &lt;record-file&gt; &lt;node-public-key-file&gt; &lt;manifest-file&gt; &lt;metadata-file&gt;
 *
 * <p>Exit codes: 0 valid, 1 usage error, 2 input error, 3 verification failed.
 */
final class VerifyRecordCommand {

    private VerifyRecordCommand() {}

    static int run(String[] args) {
        if (args.length != 4) {
            System.err.println(
                    "Usage: truthcrawl verify-record <record-file> <node-public-key-file> <manifest-file> <metadata-file>");
            return 1;
        }

        Path recordPath = Path.of(args[0]);
        Path nodeKeyPath = Path.of(args[1]);
        Path manifestPath = Path.of(args[2]);
        Path metadataPath = Path.of(args[3]);

        try {
            List<String> recordLines = Files.readAllLines(recordPath, StandardCharsets.UTF_8);
            ObservationRecord record = ObservationRecord.parse(recordLines);

            String nodePublicKey = Files.readString(nodeKeyPath, StandardCharsets.UTF_8).strip();

            List<String> manifestLines = Files.readAllLines(manifestPath, StandardCharsets.UTF_8);
            BatchManifest manifest = BatchManifest.parse(manifestLines);

            List<String> metadataLines = Files.readAllLines(metadataPath, StandardCharsets.UTF_8);
            BatchMetadata metadata = BatchMetadata.parse(metadataLines);

            RecordInclusionVerifier.Result result =
                    RecordInclusionVerifier.verify(record, nodePublicKey, manifest, metadata);

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

        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            return 2;
        }
    }
}
