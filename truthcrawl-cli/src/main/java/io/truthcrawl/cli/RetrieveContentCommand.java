package io.truthcrawl.cli;

import io.truthcrawl.core.ContentArchive;

import java.io.OutputStream;
import java.nio.file.Path;

/**
 * CLI command: retrieve-content.
 *
 * <p>Retrieves archived content by hash, writes payload to stdout.
 *
 * <p>Usage: truthcrawl retrieve-content &lt;content-hash&gt; &lt;archive-dir&gt;
 *
 * <p>Exit codes: 0 success, 1 usage error, 2 not found or runtime error.
 */
final class RetrieveContentCommand {

    private RetrieveContentCommand() {}

    static int run(String[] args) {
        if (args.length != 2) {
            System.err.println("Usage: truthcrawl retrieve-content <content-hash> <archive-dir>");
            return 1;
        }

        String contentHash = args[0];
        Path archiveDir = Path.of(args[1]);

        try {
            ContentArchive archive = new ContentArchive(archiveDir);
            byte[] payload = archive.retrieve(contentHash);
            if (payload == null) {
                System.err.println("Not found: " + contentHash);
                return 2;
            }
            OutputStream out = System.out;
            out.write(payload);
            out.flush();
            return 0;
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            return 2;
        }
    }
}
