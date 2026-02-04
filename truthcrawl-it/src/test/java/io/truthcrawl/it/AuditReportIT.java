package io.truthcrawl.it;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AuditReportIT {

    @Test
    void creates_audit_report() throws Exception {
        CliRunner.Result r = CliRunner.run("audit-report",
                "2024-01-15", "100", "10", "9", "1", "1");

        assertEquals(0, r.exitCode(), "audit-report failed: " + r.stderr());
        assertTrue(r.stdout().contains("batch_id:2024-01-15"));
        assertTrue(r.stdout().contains("records_total:100"));
        assertTrue(r.stdout().contains("records_sampled:10"));
        assertTrue(r.stdout().contains("records_matched:9"));
        assertTrue(r.stdout().contains("records_mismatched:1"));
        assertTrue(r.stdout().contains("disputes_filed:1"));
    }

    @Test
    void writes_to_output_file(@TempDir Path tmp) throws Exception {
        Path output = tmp.resolve("report.txt");
        CliRunner.Result r = CliRunner.run("audit-report",
                "2024-01-15", "50", "10", "10", "0", "0", output.toString());

        assertEquals(0, r.exitCode(), "audit-report failed: " + r.stderr());
        assertTrue(Files.exists(output));
        String content = Files.readString(output);
        assertTrue(content.contains("batch_id:2024-01-15"));
        assertTrue(content.contains("records_mismatched:0"));
    }

    @Test
    void exits_1_with_no_arguments() throws Exception {
        CliRunner.Result r = CliRunner.run("audit-report");
        assertEquals(1, r.exitCode());
    }

    @Test
    void exits_2_for_invalid_counts() throws Exception {
        // matched + mismatched != sampled
        CliRunner.Result r = CliRunner.run("audit-report",
                "2024-01-15", "100", "10", "5", "3", "0");
        assertEquals(2, r.exitCode());
    }
}
