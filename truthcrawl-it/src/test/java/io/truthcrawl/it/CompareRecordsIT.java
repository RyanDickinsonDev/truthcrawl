package io.truthcrawl.it;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CompareRecordsIT {

    private static final String NODE_ID =
            "0000000000000000000000000000000000000000000000000000000000000000";

    @Test
    void matching_records_output_match(@TempDir Path tmp) throws Exception {
        Path r1 = writeRecord(tmp, "r1.txt", 200,
                "b94d27b9934d3e08a52e52d7da7dabfac484efe37a5380ee9088f7ace2efcde9");
        Path r2 = writeRecord(tmp, "r2.txt", 200,
                "b94d27b9934d3e08a52e52d7da7dabfac484efe37a5380ee9088f7ace2efcde9");

        CliRunner.Result r = CliRunner.run("compare-records", r1.toString(), r2.toString());
        assertEquals(0, r.exitCode());
        assertTrue(r.stdout().contains("MATCH"));
    }

    @Test
    void different_records_output_mismatch(@TempDir Path tmp) throws Exception {
        Path r1 = writeRecord(tmp, "r1.txt", 200,
                "b94d27b9934d3e08a52e52d7da7dabfac484efe37a5380ee9088f7ace2efcde9");
        Path r2 = writeRecord(tmp, "r2.txt", 404,
                "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa");

        CliRunner.Result r = CliRunner.run("compare-records", r1.toString(), r2.toString());
        assertEquals(3, r.exitCode());
        assertTrue(r.stdout().contains("MISMATCH"));
    }

    @Test
    void exits_1_with_no_arguments() throws Exception {
        CliRunner.Result r = CliRunner.run("compare-records");
        assertEquals(1, r.exitCode());
    }

    private Path writeRecord(Path dir, String name, int status, String contentHash) throws IOException {
        String text = "version:0.1\n"
                + "observed_at:2024-01-15T12:00:00Z\n"
                + "url:https://example.com\n"
                + "final_url:https://example.com/\n"
                + "status_code:" + status + "\n"
                + "fetch_ms:100\n"
                + "content_hash:" + contentHash + "\n"
                + "directive:canonical:\n"
                + "directive:robots_meta:\n"
                + "directive:robots_header:\n"
                + "node_id:" + NODE_ID + "\n"
                + "node_signature:\n";
        Path file = dir.resolve(name);
        Files.writeString(file, text);
        return file;
    }
}
