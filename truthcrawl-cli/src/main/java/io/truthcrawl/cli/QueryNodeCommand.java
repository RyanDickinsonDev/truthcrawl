package io.truthcrawl.cli;

import io.truthcrawl.core.IndexBuilder;
import io.truthcrawl.core.RecordStore;

import java.nio.file.Path;
import java.util.List;

/**
 * CLI command: query-node.
 *
 * <p>Lists all record hashes for a given node_id from the record store.
 *
 * <p>Usage: truthcrawl query-node &lt;node-id&gt; &lt;store-dir&gt;
 *
 * <p>Outputs record hashes, one per line.
 *
 * <p>Exit codes: 0 success, 1 usage error, 2 input error.
 */
final class QueryNodeCommand {

    private QueryNodeCommand() {}

    static int run(String[] args) {
        if (args.length != 2) {
            System.err.println("Usage: truthcrawl query-node <node-id> <store-dir>");
            return 1;
        }

        String nodeId = args[0];
        Path storeDir = Path.of(args[1]);

        try {
            RecordStore store = new RecordStore(storeDir);
            IndexBuilder.Index index = IndexBuilder.build(store);
            List<String> hashes = index.byNode(nodeId);

            StringBuilder sb = new StringBuilder();
            for (String hash : hashes) {
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
