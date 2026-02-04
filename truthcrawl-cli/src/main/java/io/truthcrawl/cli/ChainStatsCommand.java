package io.truthcrawl.cli;

import io.truthcrawl.core.ChainStats;
import io.truthcrawl.core.RecordStore;

import java.nio.file.Path;

/**
 * CLI command: chain-stats.
 *
 * <p>Computes and displays aggregate statistics from the record store.
 *
 * <p>Usage: truthcrawl chain-stats &lt;chain-length&gt; &lt;store-dir&gt;
 *
 * <p>Exit codes: 0 success, 1 usage error, 2 input error.
 */
final class ChainStatsCommand {

    private ChainStatsCommand() {}

    static int run(String[] args) {
        if (args.length != 2) {
            System.err.println("Usage: truthcrawl chain-stats <chain-length> <store-dir>");
            return 1;
        }

        try {
            int chainLength = Integer.parseInt(args[0]);
            Path storeDir = Path.of(args[1]);

            RecordStore store = new RecordStore(storeDir);
            ChainStats stats = ChainStats.compute(chainLength, store);

            System.out.print(stats.toCanonicalText());
            return 0;

        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            return 2;
        }
    }
}
