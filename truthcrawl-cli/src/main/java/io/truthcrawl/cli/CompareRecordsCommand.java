package io.truthcrawl.cli;

import io.truthcrawl.core.ObservationRecord;
import io.truthcrawl.core.RecordComparator;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * CLI command: compare-records.
 *
 * <p>Compares two ObservationRecords field-by-field and reports discrepancies.
 *
 * <p>Usage: truthcrawl compare-records &lt;original-record-file&gt; &lt;actual-record-file&gt;
 *
 * <p>Exit codes: 0 match, 1 usage error, 2 input error, 3 mismatch.
 */
final class CompareRecordsCommand {

    private CompareRecordsCommand() {}

    static int run(String[] args) {
        if (args.length != 2) {
            System.err.println("Usage: truthcrawl compare-records <original-record-file> <actual-record-file>");
            return 1;
        }

        Path originalPath = Path.of(args[0]);
        Path actualPath = Path.of(args[1]);

        try {
            List<String> origLines = Files.readAllLines(originalPath, StandardCharsets.UTF_8);
            ObservationRecord original = ObservationRecord.parse(origLines);

            List<String> actualLines = Files.readAllLines(actualPath, StandardCharsets.UTF_8);
            ObservationRecord actual = ObservationRecord.parse(actualLines);

            RecordComparator.Result result = RecordComparator.compare(original, actual);
            System.out.print(RecordComparator.formatReport(result));

            return result.match() ? 0 : 3;

        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            return 2;
        }
    }
}
