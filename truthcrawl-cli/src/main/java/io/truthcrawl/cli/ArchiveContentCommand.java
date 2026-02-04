package io.truthcrawl.cli;

import io.truthcrawl.core.ContentArchive;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * CLI command: archive-content.
 *
 * <p>Archives content from a file, outputs the content hash.
 *
 * <p>Usage: truthcrawl archive-content &lt;file&gt; &lt;content-type&gt; &lt;archive-dir&gt;
 *
 * <p>Exit codes: 0 success, 1 usage error, 2 runtime error.
 */
final class ArchiveContentCommand {

    private ArchiveContentCommand() {}

    static int run(String[] args) {
        if (args.length != 3) {
            System.err.println("Usage: truthcrawl archive-content <file> <content-type> <archive-dir>");
            return 1;
        }

        Path inputFile = Path.of(args[0]);
        String contentType = args[1];
        Path archiveDir = Path.of(args[2]);

        try {
            byte[] payload = Files.readAllBytes(inputFile);
            ContentArchive archive = new ContentArchive(archiveDir);
            String hash = archive.store(payload, contentType);
            System.out.println(hash);
            return 0;
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            return 2;
        }
    }
}
