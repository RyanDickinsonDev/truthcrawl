package io.truthcrawl.cli;

import io.truthcrawl.core.PeerRegistry;

import java.nio.file.Path;
import java.util.List;

/**
 * CLI command: list-peers.
 *
 * <p>Lists all registered peer nodeIds.
 *
 * <p>Usage: truthcrawl list-peers &lt;peers-dir&gt;
 *
 * <p>Exit codes: 0 success, 1 usage error, 2 runtime error.
 */
final class ListPeersCommand {

    private ListPeersCommand() {}

    static int run(String[] args) {
        if (args.length != 1) {
            System.err.println("Usage: truthcrawl list-peers <peers-dir>");
            return 1;
        }

        Path peersDir = Path.of(args[0]);

        try {
            PeerRegistry registry = new PeerRegistry(peersDir);
            List<String> nodeIds = registry.listNodeIds();
            for (String id : nodeIds) {
                System.out.println(id);
            }
            return 0;

        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            return 2;
        }
    }
}
