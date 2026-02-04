package io.truthcrawl.it;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChainStatsIT {

    private void storeRecord(Path storeDir, Path tmp, String url, String nodeId,
                             String contentHash, String filename) throws Exception {
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
        Path file = tmp.resolve(filename);
        Files.writeString(file, text, StandardCharsets.UTF_8);

        CliRunner.Result r = CliRunner.run("store-record", file.toString(), storeDir.toString());
        assertEquals(0, r.exitCode(), "store-record failed: " + r.stderr());
    }

    @Test
    void computes_chain_stats(@TempDir Path tmp) throws Exception {
        Path storeDir = tmp.resolve("store");
        storeRecord(storeDir, tmp, "https://a.com", "node1", "a".repeat(64), "r1.txt");
        storeRecord(storeDir, tmp, "https://a.com", "node2", "b".repeat(64), "r2.txt");
        storeRecord(storeDir, tmp, "https://b.com", "node1", "c".repeat(64), "r3.txt");

        CliRunner.Result r = CliRunner.run("chain-stats", "5", storeDir.toString());

        assertEquals(0, r.exitCode(), "chain-stats failed: " + r.stderr());
        assertTrue(r.stdout().contains("total_batches:5"));
        assertTrue(r.stdout().contains("total_records:3"));
        assertTrue(r.stdout().contains("unique_urls:2"));
        assertTrue(r.stdout().contains("unique_nodes:2"));
    }

    @Test
    void exits_1_with_no_arguments() throws Exception {
        CliRunner.Result r = CliRunner.run("chain-stats");
        assertEquals(1, r.exitCode());
    }

    @Test
    void exits_2_for_empty_store(@TempDir Path tmp) throws Exception {
        Path storeDir = tmp.resolve("store");
        Files.createDirectories(storeDir);

        CliRunner.Result r = CliRunner.run("chain-stats", "1", storeDir.toString());

        // Empty store should still work, just show zeros
        assertEquals(0, r.exitCode(), "chain-stats failed: " + r.stderr());
        assertTrue(r.stdout().contains("total_records:0"));
        assertTrue(r.stdout().contains("unique_urls:0"));
    }
}
