package io.truthcrawl.cli;

import io.truthcrawl.core.ContentArchive;

import java.nio.file.Path;
import java.time.Instant;
import java.time.format.DateTimeFormatter;

/**
 * CLI command: archive-stats.
 *
 * <p>Displays archive statistics: count, total size, oldest/newest.
 *
 * <p>Usage: truthcrawl archive-stats &lt;archive-dir&gt;
 *
 * <p>Exit codes: 0 success, 1 usage error, 2 runtime error.
 */
final class ArchiveStatsCommand {

    private ArchiveStatsCommand() {}

    static int run(String[] args) {
        if (args.length != 1) {
            System.err.println("Usage: truthcrawl archive-stats <archive-dir>");
            return 1;
        }

        Path archiveDir = Path.of(args[0]);

        try {
            ContentArchive archive = new ContentArchive(archiveDir);
            int count = archive.count();
            long totalSize = archive.totalSize();
            Instant oldest = archive.oldest();
            Instant newest = archive.newest();

            System.out.println("count:" + count);
            System.out.println("total_bytes:" + totalSize);
            System.out.println("oldest:" + formatInstant(oldest));
            System.out.println("newest:" + formatInstant(newest));
            return 0;
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            return 2;
        }
    }

    private static String formatInstant(Instant instant) {
        if (instant == null) return "";
        return DateTimeFormatter.ISO_INSTANT.format(instant);
    }
}
