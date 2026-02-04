package io.truthcrawl.cli;

import io.truthcrawl.core.AuditReport;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * CLI command: audit-report.
 *
 * <p>Creates an audit report from verification results.
 *
 * <p>Usage: truthcrawl audit-report &lt;batch-id&gt; &lt;records-total&gt; &lt;records-sampled&gt;
 *         &lt;records-matched&gt; &lt;records-mismatched&gt; &lt;disputes-filed&gt; [&lt;output-file&gt;]
 *
 * <p>Outputs the canonical text to stdout, or writes to the output file if specified.
 *
 * <p>Exit codes: 0 success, 1 usage error, 2 input error.
 */
final class AuditReportCommand {

    private AuditReportCommand() {}

    static int run(String[] args) {
        if (args.length < 6 || args.length > 7) {
            System.err.println("Usage: truthcrawl audit-report <batch-id> <records-total>"
                    + " <records-sampled> <records-matched> <records-mismatched>"
                    + " <disputes-filed> [<output-file>]");
            return 1;
        }

        try {
            AuditReport report = new AuditReport(
                    args[0],
                    Integer.parseInt(args[1]),
                    Integer.parseInt(args[2]),
                    Integer.parseInt(args[3]),
                    Integer.parseInt(args[4]),
                    Integer.parseInt(args[5])
            );

            String output = report.toCanonicalText();

            if (args.length == 7) {
                Files.writeString(Path.of(args[6]), output, StandardCharsets.UTF_8);
            }

            System.out.print(output);
            return 0;

        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            return 2;
        }
    }
}
