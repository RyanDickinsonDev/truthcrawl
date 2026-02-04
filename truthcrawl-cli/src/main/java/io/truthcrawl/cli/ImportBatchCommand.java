package io.truthcrawl.cli;

import io.truthcrawl.core.BatchImporter;
import io.truthcrawl.core.PublisherKey;
import io.truthcrawl.core.RecordStore;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * CLI command: import-batch.
 *
 * <p>Imports a batch export directory into the local record store.
 *
 * <p>Usage: truthcrawl import-batch &lt;export-dir&gt; &lt;store-dir&gt; &lt;pub-key&gt;
 *
 * <p>Outputs the import receipt. Exit codes: 0 success, 1 usage error, 2 input error.
 */
final class ImportBatchCommand {

    private ImportBatchCommand() {}

    static int run(String[] args) {
        if (args.length != 3) {
            System.err.println("Usage: truthcrawl import-batch <export-dir> <store-dir> <pub-key>");
            return 1;
        }

        Path exportDir = Path.of(args[0]);
        Path storeDir = Path.of(args[1]);
        Path pubKeyPath = Path.of(args[2]);

        try {
            String pubKeyBase64 = Files.readString(pubKeyPath, StandardCharsets.UTF_8).strip();
            PublisherKey key = PublisherKey.fromPublicKey(pubKeyBase64);
            RecordStore store = new RecordStore(storeDir);

            BatchImporter.ImportReceipt receipt = BatchImporter.importBatch(exportDir, store, key);
            System.out.print(receipt.toCanonicalText());

            if (!receipt.valid()) {
                for (String error : receipt.errors()) {
                    System.err.println("Validation error: " + error);
                }
                return 2;
            }
            return 0;

        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            return 2;
        }
    }
}
