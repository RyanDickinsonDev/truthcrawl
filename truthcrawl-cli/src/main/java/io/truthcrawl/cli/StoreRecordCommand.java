package io.truthcrawl.cli;

import io.truthcrawl.core.ObservationRecord;
import io.truthcrawl.core.RecordStore;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * CLI command: store-record.
 *
 * <p>Stores an observation record file in the hash-addressed record store.
 *
 * <p>Usage: truthcrawl store-record &lt;record-file&gt; &lt;store-dir&gt;
 *
 * <p>Outputs the record hash. Idempotent: storing the same record twice is a no-op.
 *
 * <p>Exit codes: 0 success, 1 usage error, 2 input error.
 */
final class StoreRecordCommand {

    private StoreRecordCommand() {}

    static int run(String[] args) {
        if (args.length != 2) {
            System.err.println("Usage: truthcrawl store-record <record-file> <store-dir>");
            return 1;
        }

        Path recordPath = Path.of(args[0]);
        Path storeDir = Path.of(args[1]);

        try {
            ObservationRecord record = ObservationRecord.parse(
                    Files.readAllLines(recordPath, StandardCharsets.UTF_8));
            RecordStore store = new RecordStore(storeDir);
            String hash = store.store(record);
            System.out.print(hash);
            return 0;

        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            return 2;
        }
    }
}
