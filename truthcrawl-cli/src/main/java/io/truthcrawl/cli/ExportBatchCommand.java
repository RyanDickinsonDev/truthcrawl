package io.truthcrawl.cli;

import io.truthcrawl.core.BatchExporter;
import io.truthcrawl.core.BatchManifest;
import io.truthcrawl.core.ChainLink;
import io.truthcrawl.core.RecordStore;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * CLI command: export-batch.
 *
 * <p>Packages a published batch into a self-contained export directory.
 *
 * <p>Usage: truthcrawl export-batch &lt;manifest&gt; &lt;chain-link&gt; &lt;signature&gt; &lt;store-dir&gt; &lt;output-dir&gt;
 *
 * <p>Exit codes: 0 success, 1 usage error, 2 input error.
 */
final class ExportBatchCommand {

    private ExportBatchCommand() {}

    static int run(String[] args) {
        if (args.length != 5) {
            System.err.println("Usage: truthcrawl export-batch <manifest> <chain-link> <signature> <store-dir> <output-dir>");
            return 1;
        }

        Path manifestPath = Path.of(args[0]);
        Path chainLinkPath = Path.of(args[1]);
        Path signaturePath = Path.of(args[2]);
        Path storeDir = Path.of(args[3]);
        Path outputDir = Path.of(args[4]);

        try {
            BatchManifest manifest = BatchManifest.parse(
                    Files.readAllLines(manifestPath, StandardCharsets.UTF_8));
            ChainLink chainLink = ChainLink.parse(
                    Files.readAllLines(chainLinkPath, StandardCharsets.UTF_8));
            String signature = Files.readString(signaturePath, StandardCharsets.UTF_8).strip();
            RecordStore store = new RecordStore(storeDir);

            Path exportPath = BatchExporter.export(outputDir, chainLink, manifest, signature, store);
            System.out.print(exportPath);
            return 0;

        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            return 2;
        }
    }
}
