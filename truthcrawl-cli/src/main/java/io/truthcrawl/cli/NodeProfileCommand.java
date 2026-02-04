package io.truthcrawl.cli;

import io.truthcrawl.core.NodeProfile;
import io.truthcrawl.core.NodeProfileStore;

import java.nio.file.Path;
import java.util.List;

/**
 * CLI command: node-profile.
 *
 * <p>Displays a node's profile, or lists all profiles if no node ID is given.
 *
 * <p>Usage:
 * <pre>
 *   truthcrawl node-profile &lt;profiles-dir&gt;                  — list all node IDs
 *   truthcrawl node-profile &lt;profiles-dir&gt; &lt;node-id&gt;        — show a specific profile
 * </pre>
 *
 * <p>Exit codes: 0 success, 1 usage error, 2 input error.
 */
final class NodeProfileCommand {

    private NodeProfileCommand() {}

    static int run(String[] args) {
        if (args.length < 1 || args.length > 2) {
            System.err.println("Usage: truthcrawl node-profile <profiles-dir> [node-id]");
            return 1;
        }

        Path profilesDir = Path.of(args[0]);

        try {
            NodeProfileStore store = new NodeProfileStore(profilesDir);

            if (args.length == 1) {
                // List all node IDs
                List<String> ids = store.listNodeIds();
                if (ids.isEmpty()) {
                    System.out.println("No profiles found.");
                } else {
                    for (String id : ids) {
                        System.out.println(id);
                    }
                }
                return 0;
            }

            // Show specific profile
            String nodeId = args[1];
            NodeProfile profile = store.load(nodeId);
            if (profile == null) {
                System.err.println("Profile not found: " + nodeId);
                return 2;
            }

            System.out.print(profile.toCanonicalText());
            return 0;

        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            return 2;
        }
    }
}
