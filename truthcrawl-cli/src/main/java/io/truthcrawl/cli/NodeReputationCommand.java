package io.truthcrawl.cli;

import io.truthcrawl.core.DisputeResolver;
import io.truthcrawl.core.NodeReputation;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * CLI command: node-reputation.
 *
 * <p>Computes per-node reputation from published dispute resolutions.
 *
 * <p>Usage: truthcrawl node-reputation &lt;resolution-file-1&gt; [&lt;resolution-file-2&gt; ...]
 *
 * <p>Exit codes: 0 success, 1 usage error, 2 input error.
 */
final class NodeReputationCommand {

    private NodeReputationCommand() {}

    static int run(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: truthcrawl node-reputation <resolution-file-1>"
                    + " [<resolution-file-2> ...]");
            return 1;
        }

        try {
            List<DisputeResolver.Resolution> resolutions = new ArrayList<>();
            for (String arg : args) {
                resolutions.add(DisputeResolver.Resolution.parse(
                        Files.readAllLines(Path.of(arg), StandardCharsets.UTF_8)));
            }

            Map<String, NodeReputation.Stats> reputations = NodeReputation.compute(
                    resolutions, Map.of());

            System.out.print(NodeReputation.formatReport(reputations));
            return 0;

        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            return 2;
        }
    }
}
