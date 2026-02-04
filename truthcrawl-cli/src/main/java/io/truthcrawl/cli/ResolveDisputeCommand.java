package io.truthcrawl.cli;

import io.truthcrawl.core.DisputeRecord;
import io.truthcrawl.core.DisputeResolver;
import io.truthcrawl.core.ObservationRecord;
import io.truthcrawl.core.ObservationSet;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * CLI command: resolve-dispute.
 *
 * <p>Resolves a dispute using majority consensus across independent observations.
 *
 * <p>Usage: truthcrawl resolve-dispute &lt;dispute-file&gt; &lt;obs-file-1&gt; &lt;obs-file-2&gt; ...
 *
 * <p>Requires at least 3 observation files for the same URL.
 * Outputs resolution canonical text to stdout and the outcome as the exit code:
 * 0 = resolved (any outcome), 1 = usage error, 2 = input error.
 */
final class ResolveDisputeCommand {

    private ResolveDisputeCommand() {}

    static int run(String[] args) {
        if (args.length < 2) {
            System.err.println("Usage: truthcrawl resolve-dispute <dispute-file>"
                    + " <obs-file-1> <obs-file-2> <obs-file-3> [<obs-file-N> ...]");
            return 1;
        }

        Path disputePath = Path.of(args[0]);

        try {
            DisputeRecord dispute = DisputeRecord.parse(
                    Files.readAllLines(disputePath, StandardCharsets.UTF_8));

            List<ObservationRecord> observations = new ArrayList<>();
            for (int i = 1; i < args.length; i++) {
                observations.add(ObservationRecord.parse(
                        Files.readAllLines(Path.of(args[i]), StandardCharsets.UTF_8)));
            }

            ObservationSet obsSet = ObservationSet.of(observations);
            DisputeResolver.Resolution resolution = DisputeResolver.resolve(
                    dispute, obsSet, Instant.now());

            System.out.print(resolution.toCanonicalText());
            return 0;

        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            return 2;
        }
    }
}
