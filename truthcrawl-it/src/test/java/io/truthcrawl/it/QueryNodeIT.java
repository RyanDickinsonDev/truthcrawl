package io.truthcrawl.it;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QueryNodeIT {

    private String storeRecord(Path storeDir, Path tmp, String url, String nodeId,
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
        return r.stdout().strip();
    }

    @Test
    void queries_records_by_node(@TempDir Path tmp) throws Exception {
        Path storeDir = tmp.resolve("store");
        String h1 = storeRecord(storeDir, tmp, "https://a.com", "node1", "a".repeat(64), "r1.txt");
        String h2 = storeRecord(storeDir, tmp, "https://b.com", "node1", "b".repeat(64), "r2.txt");
        storeRecord(storeDir, tmp, "https://c.com", "node2", "c".repeat(64), "r3.txt");

        CliRunner.Result r = CliRunner.run("query-node", "node1", storeDir.toString());

        assertEquals(0, r.exitCode(), "query-node failed: " + r.stderr());
        String[] lines = r.stdout().strip().split("\n");
        assertEquals(2, lines.length);
        assertTrue(r.stdout().contains(h1));
        assertTrue(r.stdout().contains(h2));
    }

    @Test
    void returns_empty_for_unknown_node(@TempDir Path tmp) throws Exception {
        Path storeDir = tmp.resolve("store");
        storeRecord(storeDir, tmp, "https://a.com", "node1", "a".repeat(64), "r1.txt");

        CliRunner.Result r = CliRunner.run("query-node", "unknown", storeDir.toString());

        assertEquals(0, r.exitCode());
        assertEquals("", r.stdout().strip());
    }

    @Test
    void exits_1_with_no_arguments() throws Exception {
        CliRunner.Result r = CliRunner.run("query-node");
        assertEquals(1, r.exitCode());
    }
}
