package io.truthcrawl.it;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StoreRecordIT {

    private Path writeRecord(Path dir, String url, String nodeId, String contentHash) throws Exception {
        String text = "version:0.1\n"
                + "observed_at:2024-01-15T12:00:00Z\n"
                + "url:" + url + "\n"
                + "final_url:" + url + "\n"
                + "status_code:200\n"
                + "fetch_ms:100\n"
                + "content_hash:" + contentHash + "\n"
                + "directive:canonical:\n"
                + "directive:robots_meta:\n"
                + "directive:robots_header:\n"
                + "node_id:" + nodeId + "\n"
                + "node_signature:\n";
        Path file = dir.resolve("record.txt");
        Files.writeString(file, text, StandardCharsets.UTF_8);
        return file;
    }

    @Test
    void stores_record_and_returns_hash(@TempDir Path tmp) throws Exception {
        Path recordFile = writeRecord(tmp, "https://example.com", "node1", "a".repeat(64));
        Path storeDir = tmp.resolve("store");

        CliRunner.Result r = CliRunner.run("store-record", recordFile.toString(), storeDir.toString());

        assertEquals(0, r.exitCode(), "store-record failed: " + r.stderr());
        String hash = r.stdout().strip();
        assertEquals(64, hash.length());
        assertTrue(hash.matches("[0-9a-f]{64}"));
        // Verify file exists in store
        assertTrue(Files.exists(storeDir.resolve(hash.substring(0, 2)).resolve(hash + ".txt")));
    }

    @Test
    void idempotent_store(@TempDir Path tmp) throws Exception {
        Path recordFile = writeRecord(tmp, "https://example.com", "node1", "a".repeat(64));
        Path storeDir = tmp.resolve("store");

        CliRunner.Result r1 = CliRunner.run("store-record", recordFile.toString(), storeDir.toString());
        CliRunner.Result r2 = CliRunner.run("store-record", recordFile.toString(), storeDir.toString());

        assertEquals(0, r1.exitCode());
        assertEquals(0, r2.exitCode());
        assertEquals(r1.stdout(), r2.stdout());
    }

    @Test
    void exits_1_with_no_arguments() throws Exception {
        CliRunner.Result r = CliRunner.run("store-record");
        assertEquals(1, r.exitCode());
    }
}
