package io.truthcrawl.cli;

import io.truthcrawl.core.BatchManifest;
import io.truthcrawl.core.ChainLink;
import io.truthcrawl.core.VerificationSampler;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * CLI command: sample-observations.
 *
 * <p>Deterministically selects records from a batch for re-verification.
 *
 * <p>Usage: truthcrawl sample-observations &lt;manifest-file&gt; &lt;chain-link-file&gt; &lt;seed&gt; [&lt;max-sample&gt;]
 *
 * <p>Outputs selected record hashes, one per line.
 *
 * <p>Exit codes: 0 success, 1 usage error, 2 input error.
 */
final class SampleObservationsCommand {

    private SampleObservationsCommand() {}

    static int run(String[] args) {
        if (args.length < 3 || args.length > 4) {
            System.err.println("Usage: truthcrawl sample-observations <manifest-file>"
                    + " <chain-link-file> <seed> [<max-sample>]");
            return 1;
        }

        Path manifestPath = Path.of(args[0]);
        Path chainLinkPath = Path.of(args[1]);
        String userSeed = args[2];
        int maxSample = VerificationSampler.DEFAULT_SAMPLE_SIZE;
        if (args.length == 4) {
            maxSample = Integer.parseInt(args[3]);
        }

        try {
            BatchManifest manifest = BatchManifest.parse(
                    Files.readAllLines(manifestPath, StandardCharsets.UTF_8));
            ChainLink link = ChainLink.parse(
                    Files.readAllLines(chainLinkPath, StandardCharsets.UTF_8));

            List<String> selected = VerificationSampler.sample(
                    manifest, link.merkleRoot(), userSeed, maxSample);

            StringBuilder sb = new StringBuilder();
            for (String hash : selected) {
                sb.append(hash).append('\n');
            }
            System.out.print(sb);
            return 0;

        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            return 2;
        }
    }
}
